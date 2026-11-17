package com.picacompose.data

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlinx.serialization.json.Json

/**
 * 桌面端的导出：不落中间文件，直接从缓存 zip 里读图。
 *
 * pdf 用自带的小写出器（每页一张 JPEG，DCTDecode 直嵌），不引第三方库；
 * webp / avif 这类 ImageIO 解不了的页会跳过。
 */
internal object JvmExportEngine {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun export(
        targetPath: String,
        format: ExportFormat,
        comicId: String,
        title: String,
        onProgress: (Float, String) -> Unit,
    ) {
        val zipPath = ComicDownloader.cachedZipPath(comicId) ?: error("这本还没缓存好")
        val file = File(targetPath)
        file.parentFile?.mkdirs()
        BufferedOutputStream(FileOutputStream(file), 256 * 1024).use { output ->
            when (format) {
                ExportFormat.Zip -> copyZip(zipPath, output, onProgress)
                ExportFormat.Pdf -> writePdf(zipPath, output, onProgress)
                ExportFormat.Epub -> writeEpub(zipPath, title, output, onProgress)
            }
        }
        // 文件流关掉之后才算写完。
        onProgress(1f, "完成")
    }

    private fun copyZip(zipPath: String, output: OutputStream, onProgress: (Float, String) -> Unit) {
        val file = File(zipPath)
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

    private fun writePdf(zipPath: String, output: OutputStream, onProgress: (Float, String) -> Unit) {
        ZipFile(zipPath).use { zip ->
            val entries = imageEntries(zip)
            if (entries.isEmpty()) error("包里没有图片，导不出来")
            val pages = entries.mapIndexedNotNull { _, entry ->
                val head = zip.getInputStream(entry).use { it.readNBytes(64 * 1024) }
                val kind = imageKind(head)
                val (width, height) = pdfSize(head, kind) ?: return@mapIndexedNotNull null
                PdfPage(entry.name, width, height, kind)
            }
            if (pages.isEmpty()) error("这些图桌面端解不了（webp、avif 之类）")
            val writer = PdfWriter(output)
            writer.header()
            writer.catalog(pages.size)
            val count = pages.size
            pages.forEachIndexed { index, page ->
                val jpeg = pdfJpeg(zip, page) ?: WHITE_JPEG
                writer.page(index, page.width, page.height, jpeg)
                onProgress((index + 1).toFloat() / count * 0.9f, "第 ${index + 1}/$count 页")
            }
            writer.finish(count)
            onProgress(0.95f, "正在收尾…")
        }
    }

    private fun writeEpub(
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

    // ---- 图片 / zip 小工具 ----

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

    private fun pdfSize(bytes: ByteArray, kind: ImageKind): Pair<Int, Int>? = when (kind) {
        ImageKind.Png -> pngSize(bytes)
        ImageKind.Jpeg -> jpegSize(bytes)
        ImageKind.Gif -> gifSize(bytes)
        ImageKind.Bmp -> bmpSize(bytes)
        else -> null
    }

    private fun pngSize(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < 24) return null
        return beInt(bytes, 16) to beInt(bytes, 20)
    }

    private fun jpegSize(bytes: ByteArray): Pair<Int, Int>? {
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

    private fun gifSize(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < 10) return null
        return leShort(bytes, 6) to leShort(bytes, 8)
    }

    private fun bmpSize(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < 26) return null
        return leInt(bytes, 18) to leInt(bytes, 22)
    }

    private fun beInt(bytes: ByteArray, offset: Int): Int = ((bytes[offset].toInt() and 0xFF) shl 24) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
        (bytes[offset + 3].toInt() and 0xFF)

    private fun beShort(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun leShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun leInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    /** 拿一页的 JPEG 字节：原图就是 jpg 就直嵌，其它格式转成 JPEG。 */
    private fun pdfJpeg(zip: ZipFile, page: PdfPage): ByteArray? {
        val entry = zip.getEntry(page.name) ?: return null
        return if (page.kind == ImageKind.Jpeg) {
            zip.getInputStream(entry).use { it.readBytes() }
        } else {
            val source = zip.getInputStream(entry).use { ImageIO.read(it) } ?: return null
            val rgb = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
            val graphics = rgb.createGraphics()
            try {
                graphics.color = Color.WHITE
                graphics.fillRect(0, 0, source.width, source.height)
                graphics.drawImage(source, 0, 0, null)
            } finally {
                graphics.dispose()
            }
            ByteArrayOutputStream().use { out ->
                ImageIO.write(rgb, "jpg", out)
                out.toByteArray()
            }
        }
    }

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

    private data class PdfPage(
        val name: String,
        val width: Int,
        val height: Int,
        val kind: ImageKind,
    )

    /**
     * 手写一个够用的 PDF：每页一张 JPEG 图。
     *
     * 对象编号是固定的：1 catalog、2 pages，之后每页占 3 个（page / content / image）。
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

    /** 1x1 白图，给解不出来的页兜底，保证页对象不错位。 */
    private val WHITE_JPEG: ByteArray by lazy {
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, 0xFFFFFF)
        ByteArrayOutputStream().use { out ->
            ImageIO.write(image, "jpg", out)
            out.toByteArray()
        }
    }
}
