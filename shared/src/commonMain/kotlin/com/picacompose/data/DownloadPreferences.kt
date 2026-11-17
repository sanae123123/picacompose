package com.picacompose.data

/**
 * 缓存（下载）相关的偏好。
 *
 * [concurrency] 是每本同时下几页，同时也当作全局网络并发上限：同时缓存多本时，
 * 所有请求加起来不会超过这个数。默认 32；调大更快，但图床可能限流，流量也更容易跑满。
 */
class DownloadPreferences(private val kv: KeyValueStore = platformKeyValueStore()) {

    /**
     * 每本同时下几页。改完立即生效：正在跑的任务下一批页面就用新的并发。
     *
     * 超出 [MIN_CONCURRENCY]..[MAX_CONCURRENCY] 的值会被夹回来。
     */
    var concurrency: Int
        get() = clamp(kv.get(KEY_CONCURRENCY)?.toIntOrNull() ?: DEFAULT_CONCURRENCY)
        set(value) {
            val clamped = clamp(value)
            kv.put(KEY_CONCURRENCY, clamped.toString())
            ComicDownloader.onConcurrencyChanged(clamped)
        }

    companion object {
        const val DEFAULT_CONCURRENCY = 32
        const val MIN_CONCURRENCY = 4
        const val MAX_CONCURRENCY = 64

        /** 设置页里给的档位。 */
        val CHOICES = listOf(4, 8, 16, 32, 64)

        internal fun clamp(value: Int): Int = value.coerceIn(MIN_CONCURRENCY, MAX_CONCURRENCY)

        private const val KEY_CONCURRENCY = "download.concurrency"
    }
}
