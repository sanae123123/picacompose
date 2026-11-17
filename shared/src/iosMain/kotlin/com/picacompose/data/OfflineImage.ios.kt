package com.picacompose.data

/** iOS 还没有离线包，阅读器也不会拿到 [OfflineImageKey]。 */
internal actual fun offlineImageBytes(comicId: String, order: Int, index: Int): ByteArray? = null

internal actual fun offlineMetaFromZip(comicId: String): String? = null
