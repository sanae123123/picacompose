package com.picacompose.picaapi

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Dns
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** 接口域名：钉住线路时只有它走钉死的地址，别的域名一律交给 DoH。 */
private const val API_HOST = "picaapi.picacomic.com"

/** 接口请求在几台地址之间轮换。 */
private const val API_SPREAD_IPS = 3

/** DoH 答案缓存 5 分钟：图片同一台机器会连着请求，别每张图都问一遍。 */
private const val CACHE_MS = 5 * 60 * 1000L

/**
 * 全局 DoH 的 DNS 钩子。
 *
 * OkHttp 的 `Dns.lookup` 是阻塞调用，跑在 OkHttp 自己的线程上，这里直接同步发 DoH 查询；
 * 解析不出来的域名退回系统 DNS —— 开关打开不能让原本能用的东西变得不能用。
 */
private class DohDns(pinnedIps: List<String>) : Dns {

    private val pinned: List<InetAddress> = pinnedIps.mapNotNull { ip ->
        runCatching { InetAddress.getByName(ip) }.getOrNull()
    }

    private val cache = ConcurrentHashMap<String, Pair<List<InetAddress>, Long>>()

    /** 交给 OkHttp 的地址对象：同一台地址会连着请求，转一次存一次。 */
    private val addresses = ConcurrentHashMap<String, InetAddress>()

    /** 接口地址轮换用的计数器。 */
    private val turn = java.util.concurrent.atomic.AtomicInteger()

    private fun address(ip: String): InetAddress? = addresses[ip]?.let { it }
        ?: runCatching { InetAddress.getByName(ip) }.getOrNull()?.also { addresses[ip] = it }

    override fun lookup(hostname: String): List<InetAddress> {
        // 接口域名也在 Cloudflare 后面，所以图片那套 IP 池同样管用：
        // 优选出来的地址排前面，钉住的线路 IP（`/init` 下发那份）跟着兜底。
        if (pinned.isNotEmpty() && hostname.equals(API_HOST, ignoreCase = true)) {
            val pooled = ImageIps.ordered().mapNotNull(::address)
            // 首页一次会并发好几个接口请求（列表 + 首页轮播 + 通知），这里同样轮着用几台。
            val spread = minOf(pooled.size, API_SPREAD_IPS)
            val start = if (spread == 0) 0 else (turn.getAndIncrement() % spread + spread) % spread
            val rotated = pooled.drop(start) + pooled.take(start)
            val merged = (rotated + pinned).distinctBy { it.hostAddress }
            if (merged.isNotEmpty()) return merged
            return pinned
        }

        val now = System.currentTimeMillis()
        cache[hostname]?.let { (addresses, at) ->
            if (now - at < CACHE_MS) return addresses
        }

        val resolved = runCatching {
            lookupDoh(hostname).map { InetAddress.getByName(it) }
        }.getOrDefault(emptyList())

        return if (resolved.isEmpty()) {
            Dns.SYSTEM.lookup(hostname)
        } else {
            cache[hostname] = resolved to now
            resolved
        }
    }

    /** 端点按可信度排，第一个给出答案的算数；上次好用的那一路排最前面。 */
    @OptIn(ExperimentalEncodingApi::class)
    private fun lookupDoh(hostname: String): List<String> {
        val query = Base64.UrlSafe.encode(dnsQueryPacket(hostname))
        val endpoints = (listOfNotNull(DohState.preferredEndpoint) + DOH_ENDPOINTS).distinct()
        for (endpoint in endpoints) {
            val ips = runCatching { getDohAnswers("$endpoint?dns=$query") }.getOrDefault(emptyList())
            if (ips.isNotEmpty()) return ips
        }
        return emptyList()
    }
}

private fun getDohAnswers(url: String): List<String> {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 3_500
        readTimeout = 4_000
        setRequestProperty("accept", "application/dns-message")
    }
    return try {
        if (connection.responseCode != 200) return emptyList()
        connection.inputStream.use { parseAnswers(it.readBytes()) }
    } finally {
        connection.disconnect()
    }
}

actual fun dohEngine(pinnedIps: List<String>): HttpClientEngine? = OkHttp.create {
    config {
        dns(DohDns(pinnedIps))
    }
}
