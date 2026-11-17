package com.picacompose.data

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 把下载好的缓存包导成文件，直接读 zip 里的图，不落中间文件。
 *
 * zip 就原样复制；pdf 每页一张图；epub 是带目录的电子书。
 */
internal object ExportEngine {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * [onProgress] 报进度：第一个参数 0..1，第二个是一句人话（`第 12/200 页` 之类）。
     * 在 IO 线程上回调，别在里面碰界面。
     */
    fun export(
        context: Context,
        uri: Uri,
        format: ExportFormat,
        comicId: String,
        title: String,
        onProgress: (Float, String) -> Unit,
    ) {
        val zipPath = ComicDownloader.cachedZipPath(comicId) ?: error("这本还没缓存好")
        if (!File(zipPath).exists()) error("缓存包不见了，可能刚被删掉")
        val output = context.contentResolver.openOutputStream(uri) ?: error("打不开保存位置")
        output.use { stream ->
            when (format) {
                ExportFormat.Zip -> copyZip(zipPath, stream, onProgress)
                ExportFormat.Pdf -> writePdf(zipPath, stream, onProgress)
                ExportFormat.Epub -> writeEpub(zipPath, title, stream, onProgress)
            }
        }
        // 流关掉之后才算真的写完：pdf 的 writeTo、zip 的中央目录、SAF 的 flush 都在那之后。
        onProgress(1f, "完成")
    }

    /** 导出 zip 就是把缓存包原样搬出去。 */
    private fun copyZip(zipPath: String, output: OutputStream, onProgress: (Float, String) -> Unit) {
        val file = File(zipPath)
        val total = file.length().coerceAtLeast(1)
        var copied = 0L
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                copied += read
                onProgress(
                    (copied.toDouble() / total * 0.95).toFloat().coerceIn(0f, 0.95f),
                    "已写 ${formatSize(copied)} / ${formatSize(total)}",
                )
            }
        }
    }

    private fun writePdf(zipPath: String, output: OutputStream, onProgress: (Float, String) -> Unit) {
        ZipFile(zipPath).use { zip ->
            val pages = imageEntries(zip)
            if (pages.isEmpty()) error("包里没有图片，导不出来")
            val document = PdfDocument()
            try {
                pages.forEachIndexed { index, entry ->
                    // 一张一张解码、画完就回收，几百页也不至于把内存吃满。
                    val bitmap = zip.getInputStream(entry).use { BitmapFactory.decodeStream(it) }
                    if (bitmap == null) return@forEachIndexed
                    try {
                        val info = PdfDocument.PageInfo
                            .Builder(bitmap.width, bitmap.height, index + 1)
                            .create()
                        val page = document.startPage(info)
                        page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                        document.finishPage(page)
                    } finally {
                        bitmap.recycle()
                    }
                    onProgress(
                        (index + 1).toFloat() / pages.size * 0.9f,
                        "第 ${index + 1}/${pages.size} 页",
                    )
                }
                onProgress(0.92f, "正在写文件…")
                document.writeTo(output)
            } finally {
                document.close()
            }
        }
    }

    private fun writeEpub(
        zipPath: String,
        fallbackTitle: String,
        output: OutputStream,
        onProgress: (Float, String) -> Unit,
    ) {
        ZipFile(zipPath).use { zip ->
            val pages = imageEntries(zip)
            if (pages.isEmpty()) error("包里没有图片，导不出来")
            val meta = readMeta(zip)
            val title = meta?.title?.takeIf { it.isNotBlank() } ?: fallbackTitle.ifBlank { "本子" }
            val chapterTitles = meta?.chapters?.associate { it.order to it.title }.orEmpty()

            ZipOutputStream(output).use { out ->
                // epub 规范要求 mimetype 是第一个、且不压缩的条目，很多阅读器只认这个。
                val mimetype = "application/epub+zip".toByteArray()
                out.putNextEntry(
                    ZipEntry("mimetype").apply {
                        method = ZipEntry.STORED
                        size = mimetype.size.toLong()
                        compressedSize = mimetype.size.toLong()
                        crc = CRC32().apply { update(mimetype) }.value
                    },
                )
                out.write(mimetype)
                out.closeEntry()
                onProgress(0f, "准备中")

                putText(out, "META-INF/container.xml", CONTAINER_XML)
                putText(out, "OEBPS/nav.xhtml", navXhtml(pages, chapterTitles))
                putText(out, "OEBPS/content.opf", contentOpf(title, pages))
                // 每页两步：先写图，再写那一页的 xhtml。
                val steps = pages.size * 2
                var step = 0
                pages.forEachIndexed { index, entry ->
                    out.putNextEntry(ZipEntry("OEBPS/images/${entry.name.substringAfterLast('/')}"))
                    zip.getInputStream(entry).use { it.copyTo(out) }
                    out.closeEntry()
                    step++
                    onProgress(
                        step.toFloat() / steps * 0.9f,
                        "第 ${index + 1}/${pages.size} 页",
                    )
                    putText(out, "OEBPS/p_${pageId(entry.name)}.xhtml", pageXhtml(entry.name))
                    step++
                    onProgress(
                        step.toFloat() / steps * 0.9f,
                        "第 ${index + 1}/${pages.size} 页",
                    )
                }
                out.finish()
                onProgress(0.95f, "正在收尾…")
            }
        }
    }

    // ---- 小工具 ----

    /** zip 里的图片条目，按「第几话、第几张」排好。 */
    private fun imageEntries(zip: ZipFile): List<ZipEntry> =
        zip.entries().asSequence()
            .filter { !it.isDirectory && it.name.startsWith("image/") }
            .sortedWith(compareBy({ pageKey(it.name).first }, { pageKey(it.name).second }))
            .toList()

    /** `image/{话数}_{页}.jpg` → 排序号。 */
    private fun pageKey(name: String): Pair<Int, Int> {
        val base = name.substringAfterLast('/').substringBeforeLast('.')
        val parts = base.split('_')
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) to
            (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    /** `image/1_3.jpg` → `1_3`，给 xhtml 文件名用。 */
    private fun pageId(name: String): String = name.substringAfterLast('/').substringBeforeLast('.')

    private fun readMeta(zip: ZipFile): DownloadMeta? {
        val entry = zip.getEntry("metadata.json") ?: return null
        return zip.getInputStream(entry).use { input ->
            runCatching {
                json.decodeFromString(DownloadMeta.serializer(), input.readBytes().decodeToString())
            }.getOrNull()
        }
    }

    private fun putText(out: ZipOutputStream, path: String, text: String) {
        out.putNextEntry(ZipEntry(path))
        out.write(text.toByteArray())
        out.closeEntry()
    }

    private fun navXhtml(pages: List<ZipEntry>, chapterTitles: Map<Int, String>): String {
        val items = pages.joinToString("\n") { entry ->
            val (order, index) = pageKey(entry.name)
            val chapter = chapterTitles[order]?.takeIf { it.isNotBlank() } ?: "第 ${order + 1} 话"
            val label = "$chapter · 第 ${index + 1} 页"
            "<li><a href=\"p_${pageId(entry.name)}.xhtml\">${xmlEscape(label)}</a></li>"
        }
        return """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><meta charset="utf-8"/><title>目录</title></head>
<body>
<nav epub:type="toc"><h1>目录</h1><ol>
$items
</ol></nav>
</body>
</html>"""
    }

    private fun contentOpf(title: String, pages: List<ZipEntry>): String {
        val manifest = buildString {
            appendLine("""<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""")
            for (entry in pages) {
                val id = pageId(entry.name)
                appendLine("""<item id="p_$id" href="p_$id.xhtml" media-type="application/xhtml+xml"/>""")
                appendLine(
                    """<item id="img_$id" href="images/${entry.name.substringAfterLast('/')}" """ +
                        """media-type="${mediaType(entry.name)}"/>""",
                )
            }
        }
        val spine = pages.joinToString("\n") { """<itemref idref="p_${pageId(it.name)}"/>""" }
        return """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:identifier id="bookid">picacompose-${xmlEscape(title)}</dc:identifier>
<dc:title>${xmlEscape(title)}</dc:title>
<dc:language>zh-CN</dc:language>
<meta property="dcterms:modified">2020-01-01T00:00:00Z</meta>
</metadata>
<manifest>
$manifest</manifest>
<spine>
$spine
</spine>
</package>"""
    }

    private fun pageXhtml(imageEntryName: String): String {
        val file = imageEntryName.substringAfterLast('/')
        return """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><meta charset="utf-8"/><title>${xmlEscape(file)}</title>
<style>html,body{margin:0;padding:0}img{display:block;width:100%;height:auto}</style></head>
<body><img src="images/${xmlEscape(file)}" alt=""/></body>
</html>"""
    }

    private fun mediaType(name: String): String = when (name.substringAfterLast('.').lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "avif" -> "image/avif"
        "bmp" -> "image/bmp"
        else -> "image/jpeg"
    }

    private fun xmlEscape(text: String): String = buildString(text.length + 16) {
        for (ch in text) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }

    private const val CONTAINER_XML = """<?xml version="1.0" encoding="utf-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
<rootfiles>
<rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
</rootfiles>
</container>"""
}
