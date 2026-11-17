package com.picacompose.data

/**
 * iOS 还没有「缓存整本」的入口：文件系统、后台续传都得按 iOS 的规矩重做一遍。
 *
 * 这里只给一个占位实现，让共享 UI 的按钮判断 `supported` 后不画出来。
 */
actual fun platformDownloadStore(): DownloadStore = object : DownloadStore {
    override val supported: Boolean = false
    override val root: String = ""

    override fun exists(path: String): Boolean = false
    override fun mkdirs(path: String) = Unit
    override fun list(path: String): List<String> = emptyList()
    override fun delete(path: String) = Unit
    override fun deleteTree(path: String) = Unit
    override fun readText(path: String): String? = null
    override fun writeTextAtomic(path: String, text: String) = Unit
    override fun writeBytesAtomic(path: String, bytes: ByteArray) = Unit
    override fun size(path: String): Long = 0L
    override fun zipDir(dirPath: String, zipPath: String): Boolean = false
}
