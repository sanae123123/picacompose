package com.picacompose.data

/**
 * 本子缓存要用的文件操作，只碰 APP 私有目录里的 `downloads` 这一块。
 *
 * Android / Desktop 用 java.io 实现（见 `JvmDownloadStore`，两边共用）；
 * iOS 还没有下载入口，[supported] 返回 false，调用方先看这个标志，别把按钮画出来。
 */
interface DownloadStore {

    /** 当前平台能不能下载。 */
    val supported: Boolean

    /** 下载根目录（Android 是 `files/downloads`）。 */
    val root: String

    fun exists(path: String): Boolean

    fun mkdirs(path: String)

    /** 列子项的名字（文件和目录都算），目录不存在返回空。 */
    fun list(path: String): List<String>

    fun delete(path: String)

    /** 连目录带内容一起删；不存在就当成功。 */
    fun deleteTree(path: String)

    fun readText(path: String): String?

    /** 原子写文本：先写 `.tmp` 再改名，崩在中间也不会留下半截清单。 */
    fun writeTextAtomic(path: String, text: String)

    /** 原子写二进制：先写 `.part` 再改名，同名覆盖。 */
    fun writeBytesAtomic(path: String, bytes: ByteArray)

    /** 文件大小，不存在返回 0。 */
    fun size(path: String): Long

    /**
     * 把目录整份打成 zip：先写 `{zipPath}.tmp`，打完再改名成 `zipPath`。
     *
     * 所以 `zipPath` 要么不存在、要么是一份完整的包。失败返回 false，`.tmp` 会清掉。
     */
    fun zipDir(dirPath: String, zipPath: String): Boolean

    /**
     * 原地重写 zip：旧包里 [keep] 为 true 的条目原样搬过去，再把 [appendDir] 里的文件补进去。
     *
     * 给「增量更新」用：不用解压整包，只把没变的话原样复制、变的话换成刚下的图。
     * 先写 `{srcZip}.tmp`，写完再改名覆盖 —— 中途失败旧包还在，不会两空。
     */
    fun rewriteZip(srcZip: String, keep: (entryName: String) -> Boolean, appendDir: String?): Boolean
}

/** 平台默认实现；Android 宿主要先调 `initDownloadStorage(context)`。 */
expect fun platformDownloadStore(): DownloadStore
