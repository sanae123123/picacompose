package com.picacompose.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/** jpeg 原图直接嵌；其它格式先用系统解码器解成位图，再转 jpeg。 */
internal actual fun decodeForPdf(bytes: ByteArray, kind: ImageKind): PdfImageData? {
    if (kind == ImageKind.Jpeg) {
        val size = jpegSize(bytes) ?: return null
        return PdfImageData(size.first, size.second, bytes)
    }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    return try {
        val jpeg = bitmap.toJpeg() ?: return null
        PdfImageData(bitmap.width, bitmap.height, jpeg)
    } finally {
        bitmap.recycle()
    }
}

internal actual fun blankPdfImage(): PdfImageData {
    val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(0xFFFFFFFF.toInt())
    return try {
        PdfImageData(1, 1, bitmap.toJpeg() ?: ByteArray(0))
    } finally {
        bitmap.recycle()
    }
}

private fun Bitmap.toJpeg(quality: Int = 90): ByteArray? {
    val out = ByteArrayOutputStream()
    return if (compress(Bitmap.CompressFormat.JPEG, quality, out)) out.toByteArray() else null
}
