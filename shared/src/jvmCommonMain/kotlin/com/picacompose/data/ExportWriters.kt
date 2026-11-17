package com.picacompose.data

import java.io.File
import java.io.OutputStream
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json

/**
 * 导出文件时真正写字节的那部分，Android 和桌面共用。
 *
 * 进度都按「写到哪了」实时报：pdf 每页写完就报一次，不是先攒着再一次性写。
 */

/** 平台解出来的一页图：宽高 + 能直接塞进 PDF 的 JPEG 字节。 */
internal class PdfImageData(
    val width: Int,
    val height: Int,
    val jpeg: ByteArray,
)

/** 把图片字节转成 PDF 能用的 JPEG；解不了就返回 null（调用方用 [blankPdfImage] 占位）。 */
internal expect fun decodeForPdf(bytes: ByteArray, kind: ImageKind): PdfImageData?

/** 解不了的页用它占位，保证 PDF 页对象不错位。 */
internal expect fun blankPdfImage(): PdfImageData

private val exportJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** zip 导出就是原样复制缓存包，0.95 封顶，剩下留给关流。 */
internal fun copyZipExport(srcZip: String, output: OutputStream, onProgress: (Float, String) -> Unit) {
    val file = File(srcZip)
    val total = file.length().coerceAtLeast(1)
    var copied = 0L
    file.inputStream().use { input ->
        val buffer = ByteArray(256 * 1024)
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

/**
 * PDF：每页写一页，写完就报进度。
 *
 * 不用系统的 PdfDocument（它会先攒在内存里、最后一次性 writeTo，中间没法报进度）；
 * 这里直接手写 PDF 结构，JPEG 用 DCTDecode 原样嵌。
 */
internal fun writePdfExport(zipPath: String, output: OutputStream, onProgress: (Float, String) -> Unit) {
    ZipFile(zipPath).use { zip ->
        val entries = imageEntries(zip)
        if (entries.isEmpty()) error("包里没有图片，导不出来")
        val writer = PdfWriter(output)
        writer.header()
        writer.catalog(entries.size)
        entries.forEachIndexed { index, entry ->
            // 一次只读一页，读完就写，内存不会跟着页数涨。
            val bytes = zip.getInputStream(entry).use { it.readBytes() }
            val image = decodeForPdf(bytes, imageKind(bytes)) ?: blankPdfImage()
            writer.page(index, image.width, image.height, image.jpeg)
            onProgress(
                (index + 1).toFloat() / entries.size * 0.98f,
                "第 ${index + 1}/${entries.size} 页",
            )
        }
        writer.finish(entries.size)
    }
}

/** epub：带目录的电子书，图片直接从缓存 zip 流对拷。 */
internal fun writeEpubExport(
    zipPath: String,
    fallbackTitle: String,
    output: OutputStream,
    onProgress: (Float, String) -> Unit,
) {
    ZipFile(zipPath).use { zip ->
        val entries = imageEntries(zip)
        if (entries.isEmpty()) error("包里没有图片，导不出来")
        val meta = readMeta(zip)
        val title = meta?.title?.takeIf { it.isNotBlank() } ?: fallbackTitle.ifBlank { "本子" }
        val chapterTitles = meta?.chapters?.associate { it.order to it.title }.orEmpty()
        ZipOutputStream(output).use { out ->
            // epub 规范要求 mimetype 是第一个、且不压缩的条目。
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
            putText(out, "META-INF/container.xml", CONTAINER_XML)
            putText(out, "OEBPS/nav.xhtml", navXhtml(entries, chapterTitles))
            putText(out, "OEBPS/content.opf", contentOpf(title, entries))
            val steps = entries.size * 2
            var step = 0
            entries.forEachIndexed { index, entry ->
                out.putNextEntry(ZipEntry("OEBPS/images/${entry.name.substringAfterLast('/')}"))
                zip.getInputStream(entry).use { it.copyTo(out) }
                out.closeEntry()
                step++
                onProgress(step.toFloat() / steps * 0.9f, "第 ${index + 1}/${entries.size} 页")
                putText(out, "OEBPS/p_${pageId(entry.name)}.xhtml", pageXhtml(entry.name))
                step++
                onProgress(step.toFloat() / steps * 0.9f, "第 ${index + 1}/${entries.size} 页")
            }
            out.finish()
            onProgress(0.95f, "正在收尾…")
        }
    }
}

// ---- zip / 元数据 ----

private fun imageEntries(zip: ZipFile): List<ZipEntry> =
    zip.entries().asSequence()
        .filter { !it.isDirectory && it.name.startsWith("image/") }
        .sortedWith(compareBy({ pageKey(it.name).first }, { pageKey(it.name).second }))
        .toList()

private fun pageKey(name: String): Pair<Int, Int> {
    val base = name.substringAfterLast('/').substringBeforeLast('.')
    val parts = base.split('_')
    return (parts.getOrNull(0)?.toIntOrNull() ?: 0) to
        (parts.getOrNull(1)?.toIntOrNull() ?: 0)
}

private fun pageId(name: String): String = name.substringAfterLast('/').substringBeforeLast('.')

private fun readMeta(zip: ZipFile): DownloadMeta? {
    val entry = zip.getEntry("metadata.json") ?: return null
    return zip.getInputStream(entry).use { input ->
        runCatching {
            exportJson.decodeFromString(DownloadMeta.serializer(), input.readBytes().decodeToString())
        }.getOrNull()
    }
}

/** JPEG 的宽高：扫到 SOF 段就有了。给「原图就是 jpeg 直接嵌」用。 */
internal fun jpegSize(bytes: ByteArray): Pair<Int, Int>? {
    if (bytes.size < 4) return null
    var i = 2
    while (i + 9 < bytes.size) {
        if ((bytes[i].toInt() and 0xFF) != 0xFF) {
            i++
            continue
        }
        val marker = bytes[i + 1].toInt() and 0xFF
        if (marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) {
            i += 2
            continue
        }
        val length = beShort(bytes, i + 2)
        if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
            return beShort(bytes, i + 7) to beShort(bytes, i + 5)
        }
        i += 2 + length
    }
    return null
}

private fun beShort(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

// ---- XHTML / OPF ----

private fun putText(out: ZipOutputStream, path: String, text: String) {
    out.putNextEntry(ZipEntry(path))
    out.write(text.toByteArray())
    out.closeEntry()
}

private fun navXhtml(entries: List<ZipEntry>, chapterTitles: Map<Int, String>): String {
    val items = entries.joinToString("\n") { entry ->
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

private fun contentOpf(title: String, entries: List<ZipEntry>): String {
    val manifest = buildString {
        appendLine("""<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""")
        for (entry in entries) {
            val id = pageId(entry.name)
            appendLine("""<item id="p_$id" href="p_$id.xhtml" media-type="application/xhtml+xml"/>""")
            appendLine(
                """<item id="img_$id" href="images/${entry.name.substringAfterLast('/')}" """ +
                    """media-type="${mediaType(entry.name)}"/>""",
            )
        }
    }
    val spine = entries.joinToString("\n") { """<itemref idref="p_${pageId(it.name)}"/>""" }
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

/**
 * 手写一个够用的 PDF：每页一张 JPEG 图。
 *
 * 对象编号固定：1 catalog、2 pages，之后每页占 3 个（page / content / image）。
 * 边写边记偏移，最后补 xref 和 trailer。
 */
private class PdfWriter(private val out: OutputStream) {

    private val offsets = HashMap<Int, Long>()
    private var position = 0L

    fun header() {
        write("%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n")
    }

    fun catalog(pageCount: Int) {
        val kids = (0 until pageCount).joinToString(" ") { "${3 + it * 3} 0 R" }
        begin(1)
        write("<< /Type /Catalog /Pages 2 0 R >>\n")
        end()
        begin(2)
        write("<< /Type /Pages /Kids [$kids] /Count $pageCount >>\n")
        end()
    }

    fun page(index: Int, width: Int, height: Int, jpeg: ByteArray) {
        val pageId = 3 + index * 3
        val contentId = pageId + 1
        val imageId = pageId + 2
        begin(pageId)
        write(
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $width $height] " +
                "/Resources << /XObject << /Im0 $imageId 0 R >> >> " +
                "/Contents $contentId 0 R >>\n",
        )
        end()
        val content = "q\n$width 0 0 $height 0 0 cm\n/Im0 Do\nQ\n"
        begin(contentId)
        write("<< /Length ${content.length} >>\nstream\n$content\nendstream\n")
        end()
        begin(imageId)
        write(
            "<< /Type /XObject /Subtype /Image /Width $width /Height $height " +
                "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode " +
                "/Length ${jpeg.size} >>\nstream\n",
        )
        writeRaw(jpeg)
        write("\nendstream\n")
        end()
    }

    fun finish(pageCount: Int) {
        val xref = position
        val total = 2 + pageCount * 3
        write("xref\n0 ${total + 1}\n")
        write("0000000000 65535 f \n")
        for (id in 1..total) {
            val offset = offsets[id] ?: 0L
            write(String.format(Locale.ROOT, "%010d 00000 n \n", offset))
        }
        write("trailer\n<< /Size ${total + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
    }

    private fun begin(id: Int) {
        offsets[id] = position
        write("$id 0 obj\n")
    }

    private fun end() {
        write("endobj\n")
    }

    private fun write(text: String) = writeRaw(text.toByteArray(Charsets.ISO_8859_1))

    private fun writeRaw(bytes: ByteArray) {
        out.write(bytes)
        position += bytes.size
    }
}

private const val CONTAINER_XML = """<?xml version="1.0" encoding="utf-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
<rootfiles>
<rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
</rootfiles>
</container>"""
