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

    override fun lookup(hostname: String): List<InetAddress> {
        if (pinned.isNotEmpty() && hostname.equals(API_HOST, ignoreCase = true)) return pinned

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
