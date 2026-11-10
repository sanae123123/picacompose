package com.picacompose.picaapi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlin.time.TimeSource

/** 探测目标：任意一个 picacomic 域名都行，只要它跟图床在同一个 zone。 */
private const val PROBE_URL = "https://picaapi.picacomic.com/"

private const val PROBE_TIMEOUT_MS = 2_500L

private val probeClient = OkHttpClient.Builder()
    .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    .callTimeout(PROBE_TIMEOUT_MS * 2, TimeUnit.MILLISECONDS)
    .retryOnConnectionFailure(false)
    .build()

/**
 * 拿单台地址去试一次，能用就返回耗时（毫秒），不能用返回 null。
 *
 * 403 是 Cloudflare 对「直连 IP、SNI 没配到这块 zone」的拒绝，这种地址放池子里只会拖慢加载；
 * 000（连不上 / 握手失败）自然也是 null。
 */
private fun probeOne(ip: String): Long? = runCatching {
    val address = InetAddress.getByName(ip)
    // DNS 直接钉死成这一台：TLS 的 SNI / 证书仍按域名算，换的只是 TCP 目标。
    val client = probeClient.newBuilder().dns(Dns { listOf(address) }).build()
    val request = Request.Builder().url(PROBE_URL).head().build()
    val start = TimeSource.Monotonic.markNow()
    client.newCall(request).execute().use { response ->
        if (response.code == 403 || response.code >= 500) return null
        start.elapsedNow().inWholeMilliseconds
    }
}.getOrNull()

actual suspend fun probeImageIps(candidates: List<String>, limit: Int): List<String> =
    withContext(Dispatchers.IO) {
        val results = coroutineScope {
            candidates.distinct().map { ip ->
                async { ip to probeOne(ip) }
            }.awaitAll()
        }
        results.mapNotNull { (ip, elapsed) -> elapsed?.let { ip to it } }
            .sortedBy { it.second }
            .map { it.first }
            .take(limit)
    }
