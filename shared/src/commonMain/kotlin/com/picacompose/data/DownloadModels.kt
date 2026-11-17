package com.picacompose.data

import kotlinx.serialization.Serializable

/**
 * 下好的本子打成 zip 时放在根目录的元数据（`metadata.json`）。
 *
 * 图片在 `image/` 下面，文件名是 `{话数}_{页下标}.{ext}`，和 [DownloadedChapter] 对得上，
 * 以后要离线阅读不用再访问接口。
 */
@Serializable
data class DownloadMeta(
    val version: Int = 1,
    val comicId: String,
    val title: String,
    val author: String? = null,
    val cover: String? = null,
    /** 下载完成时间，毫秒。 */
    val downloadedAt: Long = 0,
    val chapters: List<DownloadedChapter> = emptyList(),
    val pages: Int = 0,
)

/** 元数据里的一话。 */
@Serializable
data class DownloadedChapter(
    val order: Int,
    val title: String = "",
    val pages: Int = 0,
    /** 服务端这一话的更新时间；下次同步时和它对一下就知道有没有变。 */
    val updatedAt: String? = null,
)

/**
 * 下载任务的清单（存在 `work/{comicId}/task.json`），进程重启后靠它接着下。
 *
 * 章节的图片列表不塞在这里：每呼一次 `pages` 接口就写一份 `plan/{order}.json`，
 * 清单保持小而稳，写坏了也只影响一话。
 */
@Serializable
data class DownloadTask(
    val comicId: String,
    val title: String,
    val author: String? = null,
    val cover: String? = null,
    val createdAt: Long = 0,
    val chapters: List<ChapterPlan> = emptyList(),
)

/** 一话：序号、标题，以及每页的图床坐标。 */
@Serializable
data class ChapterPlan(
    val order: Int,
    val title: String = "",
    val pages: List<PagePlan> = emptyList(),
    /** 服务端这一话的更新时间，打包时带进元数据。 */
    val updatedAt: String? = null,
)

/**
 * 一页的图床坐标：接口里 `media` 那两段（fileServer + path）原样存。
 *
 * 不存拼好的 URL —— 换线路、换图床之后重新拼一遍才对得上。
 */
@Serializable
data class PagePlan(
    val fileServer: String? = null,
    val path: String? = null,
)

/** 一个本子的缓存进度，详情页右上角按这个画。 */
sealed interface DownloadStatus {
    /** 没下过。 */
    data object Idle : DownloadStatus

    /** 正在拉章节和每话的图片列表。 */
    data object Preparing : DownloadStatus

    /** 正在下图：已完成 [done] 页 / 共 [total] 页。 */
    data class Running(val done: Int, val total: Int) : DownloadStatus

    /** 下好并打包完了，[bytes] 是 zip 大小。 */
    data class Completed(val bytes: Long) : DownloadStatus

    /** 中途失败（网络彻底断了、或者有几页怎么都取不到），目录留着，再点一次会接着下。 */
    data class Failed(val message: String) : DownloadStatus
}
