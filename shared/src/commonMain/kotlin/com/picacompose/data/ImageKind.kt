package com.picacompose.data

/**
 * 图片真实格式：看文件头认，不看 URL 后缀。
 *
 * 图床给的后缀不一定靠得住（`.jpg` 的地址回来一张 webp 很常见），
 * 下载下来按内容定扩展名，本地包里的文件名才对得上真正的内容。
 */
internal enum class ImageKind(val ext: String, val mime: String) {
    Png("png", "image/png"),
    Jpeg("jpg", "image/jpeg"),
    Webp("webp", "image/webp"),
    Gif("gif", "image/gif"),
    Avif("avif", "image/avif"),
    Bmp("bmp", "image/bmp"),
    Unknown("jpg", "image/jpeg"),
}

/** 找本地图片时按这些后缀挨个试（顺序无所谓，名字带对了就行）。 */
internal val IMAGE_EXTENSIONS = listOf("png", "jpg", "jpeg", "webp", "gif", "avif", "bmp")

/** 按文件头判断格式；认不出来按 jpg 算，Coil 解码时还会再嗅一次。 */
internal fun imageKind(bytes: ByteArray): ImageKind {
    fun at(offset: Int, vararg values: Int): Boolean {
        if (offset < 0 || offset + values.size > bytes.size) return false
        for (i in values.indices) {
            if ((bytes[offset + i].toInt() and 0xFF) != values[i]) return false
        }
        return true
    }

    return when {
        at(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> ImageKind.Png
        at(0, 0xFF, 0xD8, 0xFF) -> ImageKind.Jpeg
        at(0, 0x52, 0x49, 0x46, 0x46) && at(8, 0x57, 0x45, 0x42, 0x50) -> ImageKind.Webp
        at(0, 0x47, 0x49, 0x46, 0x38) -> ImageKind.Gif
        at(4, 0x66, 0x74, 0x79, 0x70) &&
            (at(8, 0x61, 0x76, 0x69, 0x66) || at(8, 0x61, 0x76, 0x69, 0x73)) -> ImageKind.Avif
        at(0, 0x42, 0x4D) -> ImageKind.Bmp
        else -> ImageKind.Unknown
    }
}
