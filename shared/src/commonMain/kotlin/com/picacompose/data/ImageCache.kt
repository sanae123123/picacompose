package com.picacompose.data

import coil3.PlatformContext
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 图片缓存：Coil 的内存缓存 + 磁盘缓存。
 *
 * 磁盘缓存是「看过哪些图就留一份」，翻本子多了能攒到几百兆；设置页给个按钮一起清掉。
 * 清的是图片，不碰登录态、线路和阅读进度。
 */
suspend fun imageCacheBytes(context: PlatformContext): Long = withContext(Dispatchers.IO) {
    runCatching { SingletonImageLoader.get(context).diskCache?.size ?: 0L }.getOrDefault(0L)
}

/** 清掉内存和磁盘里的图片，返回清完后的占用（正常是 0）。 */
suspend fun clearImageCache(context: PlatformContext): Long = withContext(Dispatchers.IO) {
    val loader = SingletonImageLoader.get(context)
    runCatching { loader.memoryCache?.clear() }
    runCatching { loader.diskCache?.clear() }
    runCatching { loader.diskCache?.size ?: 0L }.getOrDefault(0L)
}

/** 缓存大小给用户看：0 B / 356 KB / 12.3 MB / 1.4 GB。 */
fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024} KB"
    bytes < 1024L * 1024L * 1024L -> "${oneDecimal(bytes, 1024L * 1024L)} MB"
    else -> "${oneDecimal(bytes, 1024L * 1024L * 1024L)} GB"
}

private fun oneDecimal(bytes: Long, unit: Long): String {
    val tenths = bytes * 10 / unit
    return "${tenths / 10}.${tenths % 10}"
}
