package com.picacompose.data

/**
 * 通知栏里的缓存进度，Android 才有；别的地方 [supported] 是 false，调用方直接跳过。
 *
 * 每本一行（进度）+ 整体页数 + 速度，展开通知能看到明细。
 */
internal data class DownloadNotice(
    /** 正在缓存的本子，按开始顺序排。 */
    val items: List<DownloadNoticeItem>,
    /** 所有活跃任务加起来：已下页数 / 总页数（准备阶段的还没算进来）。 */
    val done: Int,
    val total: Int,
    /** 这次缓存已经下了多少字节。 */
    val bytes: Long,
    /** 当前速度（字节/秒），0 表示还测不出来。 */
    val bytesPerSecond: Long,
)

internal data class DownloadNoticeItem(
    val title: String,
    val done: Int,
    val total: Int,
)

internal expect object DownloadNotifier {

    val supported: Boolean

    /** [notice] 为 null 表示全部收工，把通知撤掉。 */
    fun update(notice: DownloadNotice?)
}
