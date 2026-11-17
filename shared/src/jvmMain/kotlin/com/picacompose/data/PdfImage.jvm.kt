package com.picacompose.data

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** jpeg 原图直接嵌；png / gif / bmp 用 ImageIO 解，再转 jpeg。 */
internal actual fun decodeForPdf(bytes: ByteArray, kind: ImageKind): PdfImageData? {
    if (kind == ImageKind.Jpeg) {
        val size = jpegSize(bytes) ?: return null
        return PdfImageData(size.first, size.second, bytes)
    }
    val source = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
    return PdfImageData(source.width, source.height, source.toJpeg())
}

internal actual fun blankPdfImage(): PdfImageData {
    val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
    image.setRGB(0, 0, 0xFFFFFF)
    return PdfImageData(1, 1, image.toJpeg())
}

private fun BufferedImage.toJpeg(): ByteArray {
    val rgb = if (type == BufferedImage.TYPE_INT_RGB) {
        this
    } else {
        // 带透明通道的图先铺白底，不然 JPEG 会把透明变黑。
        val copy = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = copy.createGraphics()
        try {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, width, height)
            graphics.drawImage(this, 0, 0, null)
        } finally {
            graphics.dispose()
        }
        copy
    }
    return ByteArrayOutputStream().use { out ->
        ImageIO.write(rgb, "jpg", out)
        out.toByteArray()
    }
}
