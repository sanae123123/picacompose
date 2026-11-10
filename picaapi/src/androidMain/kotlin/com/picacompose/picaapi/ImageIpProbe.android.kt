package com.picacompose.picaapi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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

/** 同时开多少台一起测：控制在 12 台，既快又不跟前台图片抢连接。 */
private const val PROBE_PARALLELISM = 12

actual suspend fun probeImageIps(
    candidates: List<String>,
    limit: Int,
    thresholdMs: Long,
    onProgress: ((tested: Int, total: Int, fastestMs: Long?) -> Unit)?,
    onHit: ((ip: String, latencyMs: Long) -> Unit)?,
): List<String> = withContext(Dispatchers.IO) {
    val gate = Semaphore(PROBE_PARALLELISM)
    val unique = candidates.distinct()
    val done = AtomicInteger(0)
    val fastest = AtomicLong(Long.MAX_VALUE)
    val results = coroutineScope {
        unique.map { ip ->
            async {
                val elapsed = gate.withPermit { probeOne(ip) }
                val tested = done.incrementAndGet()
                if (elapsed != null) {
                    fastest.updateAndGet { old -> minOf(old, elapsed) }
                    // 一测到够快的就先交给上面用着，剩下的继续测。
                    if (elapsed <= thresholdMs) onHit?.invoke(ip, elapsed)
                }
                onProgress?.invoke(
                    tested,
                    unique.size,
                    fastest.get().takeIf { it != Long.MAX_VALUE },
                )
                ip to elapsed
            }
        }.awaitAll()
    }
    val usable = results.mapNotNull { (ip, elapsed) -> elapsed?.let { ip to it } }
        .sortedBy { it.second }
    val fast = usable.filter { it.second <= thresholdMs }
    // 500ms 以内的按顺序先上；凑不满就从剩下的里面按延迟补，池子不至于只剩一两台。
    val chosen = when {
        fast.size >= limit -> fast.take(limit)
        else -> (fast + usable.drop(fast.size)).take(limit)
    }
    chosen.map { it.first }
}
