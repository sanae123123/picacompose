package com.picacompose.data

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer
import okio.FileSystem

/**
 * 让 Coil 直接去 zip 里取图：阅读器已缓存的话，图片请求不走网络。
 *
 * 不做解压 —— 打开 zip 找条目、读一个 entry，就这一张图。
 * 没有这个能力（iOS）时 [offlineImageBytes] 返回 null，Fetcher 会返回 null 让 Coil 报错，
 * 但阅读器只在 [OfflineLibrary.hasPage] 为 true 时才用这个 key，正常不会走到。
 */
internal data class OfflineImageKey(
    val comicId: String,
    val order: Int,
    val index: Int,
    /** 包的时间戳：增量更新后同一个 key 的内容会变，带上它内存缓存才不会拿旧图。 */
    val stamp: Long = 0,
)

/** 从 zip 里读一页图的字节；没有就返回 null。 */
internal expect fun offlineImageBytes(comicId: String, order: Int, index: Int): ByteArray?

/** 从 zip 根目录读 `metadata.json` 原文；没有就返回 null。 */
internal expect fun offlineMetaFromZip(comicId: String): String?

private class OfflineImageFetcher(
    private val key: OfflineImageKey,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val bytes = offlineImageBytes(key.comicId, key.order, key.index) ?: return null
        return SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), FileSystem.SYSTEM),
            mimeType = imageKind(bytes).mime,
            dataSource = DataSource.DISK,
        )
    }
}

/** 只认 [OfflineImageKey]；别的 data 交还给后面的 fetcher。 */
internal object OfflineImageFetcherFactory : Fetcher.Factory<OfflineImageKey> {
    override fun create(data: OfflineImageKey, options: Options, imageLoader: ImageLoader): Fetcher =
        OfflineImageFetcher(data, options)
}
