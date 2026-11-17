package com.picacompose.data

/**
 * 通知栏里的缓存进度，Android 才有；别的地方 [supported] 是 false，调用方直接跳过。
 *
 * 只报「整体」：几本、多少页，不按本拆。多本一起下的时候通知栏换来换去反而乱。
 */
internal data class DownloadNotice(
    /** 正在缓存几本。 */
    val books: Int,
    /** 已下页数（所有本加起来）。 */
    val done: Int,
    /** 总页数；准备阶段还不知道，传 0 让通知栏画不确定进度。 */
    val total: Int,
)

internal expect object DownloadNotifier {

    val supported: Boolean

    /** [notice] 为 null 表示全部收工，把通知撤掉。 */
    fun update(notice: DownloadNotice?)
}
