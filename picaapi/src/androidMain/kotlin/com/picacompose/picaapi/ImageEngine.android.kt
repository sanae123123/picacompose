package com.picacompose.picaapi

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

private const val PICACOMIC_SUFFIX = "picacomic.com"

/**
 * 图片请求专用的 DNS：picacomic 的域名按 [ImageIps] 的顺序钉地址，系统解析出来的排在后面兜底。
 *
 * 顺序很关键 —— 被污染的系统解析结果是「能解析、连不上」，放前面就是每张图先等一轮超时。
 * 池子会随后台优选结果变化，所以每次 lookup 都现取，不做整份缓存。
 */
private class ImageDns : Dns {

    /** 交给 OkHttp 的地址对象：同一台地址会连着请求，转一次存一次。 */
    private val cache = java.util.concurrent.ConcurrentHashMap<String, InetAddress>()

    /** 轮流用几台地址用的计数器；每次 lookup 往后挪一位。 */
    private val turn = java.util.concurrent.atomic.AtomicInteger()

    private fun address(ip: String): InetAddress? = cache[ip]?.let { it }
        ?: runCatching { InetAddress.getByName(ip) }.getOrNull()?.also { cache[ip] = it }

    override fun lookup(hostname: String): List<InetAddress> {
        if (!hostname.endsWith(PICACOMIC_SUFFIX, ignoreCase = true)) return Dns.SYSTEM.lookup(hostname)
        val pooled = ImageIps.ordered().mapNotNull(::address)
        if (pooled.isEmpty()) {
            val system = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrDefault(emptyList())
            if (system.isEmpty()) throw UnknownHostException("Unable to resolve host \"$hostname\"")
            return system
        }
        // 一屏图几十个并发连接：池子里最快的那几台轮着上，别全挤在同一台上。
        // OkHttp 对同一个 host 会复用连接，所以只有新开连接时才真的换台，但那也正好是「同时下多张图」的时候。
        val spread = minOf(pooled.size, SPREAD_IPS)
        val start = (turn.getAndIncrement() % spread + spread) % spread
        val rotated = pooled.drop(start) + pooled.take(start)
        // 系统解析出来的地址排在最后兜底：被污染时连过去只会超时，不能放前面。
        val system = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrDefault(emptyList())
        return (rotated + system).distinctBy { it.hostAddress }
    }
}

/** 并发下载时最多在几台地址之间轮换。 */
private const val SPREAD_IPS = 3

actual fun imageEngine(): HttpClientEngine? = OkHttp.create {
    config {
        dns(ImageDns())
        // OkHttp 默认同一个 host 只给 5 个并发请求，一屏十几张图会排队。
        // 图床是同一个域名，这里把并发放开一点，一屏图能一起下。
        dispatcher(
            okhttp3.Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 16
            },
        )
    }
}
