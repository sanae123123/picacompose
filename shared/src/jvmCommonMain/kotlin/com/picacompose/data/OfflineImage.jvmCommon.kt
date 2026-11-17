package com.picacompose.data

import java.io.File
import java.util.zip.ZipFile

internal actual fun offlineImageBytes(comicId: String, order: Int, index: Int): ByteArray? {
    val root = platformDownloadStore().root
    if (root.isBlank()) return null
    val zip = File(root, "$comicId.zip")
    if (!zip.isFile) return null
    return runCatching {
        ZipFile(zip).use { zf ->
            for (ext in IMAGE_EXTENSIONS) {
                val entry = zf.getEntry("image/${order}_$index.$ext") ?: continue
                return@use zf.getInputStream(entry).use { it.readBytes() }
            }
            null
        }
    }.getOrNull()
}

internal actual fun offlineMetaFromZip(comicId: String): String? {
    val root = platformDownloadStore().root
    if (root.isBlank()) return null
    val zip = File(root, "$comicId.zip")
    if (!zip.isFile) return null
    return runCatching {
        ZipFile(zip).use { zf ->
            val entry = zf.getEntry("metadata.json") ?: return@use null
            zf.getInputStream(entry).use { it.readBytes().decodeToString() }
        }
    }.getOrNull()
}
