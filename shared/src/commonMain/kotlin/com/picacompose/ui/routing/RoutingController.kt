package com.picacompose.ui.routing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.picacompose.data.LineRouter
import com.picacompose.data.PicaLine
import com.picacompose.data.RouteStore
import com.picacompose.data.LinePreferences
import com.picacompose.data.applyPicaRoute
import com.picacompose.data.dohAddressesIfEnabled
import com.picacompose.picaapi.dohEngine
import com.picacompose.picaapi.fetchBootstrap
import com.picacompose.picaapi.pinnedEngine
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.time.TimeSource

/** 一条线路的测速状态。 */
data class LineProbe(
    val line: PicaLine,
    val latencyMs: Long? = null,
    val done: Boolean = false,
)

/**
 * 冷启动第一屏的逻辑：拉 `/init` 拿到 IP 池 → 给线路测速 → 自动选最快的一条。
 *
 * 实际只打两次点：系统解析、下发 IP。线路 2/3 走同一批 IP，只是通道号不同，
 * 所以共用同一个数值。
 */
class RoutingController(
    private val store: RouteStore = RouteStore(),
    private val preferences: LinePreferences = LinePreferences(),
) {

    var loading by mutableStateOf(true)
        private set

    var ips by mutableStateOf(store.savedIps)
        private set

    var probes by mutableStateOf(PicaLine.entries.map { LineProbe(it) })
        private set

    var chosen by mutableStateOf<PicaLine?>(null)
        private set

    var note by mutableStateOf<String?>(null)
        private set

    /** 上次用的线路，界面上标一下方便对比。 */
    val lastLine: PicaLine? = store.savedLine

    suspend fun run() {
        loading = true
        chosen = null
        note = null
        probes = PicaLine.entries.map { LineProbe(it) }
        val savedIps = ips

        // 拉列表和测速并行跑：系统解析不需要 IP 池，用上次存的池也能先测起来，
        // 冷启动就少等一个来回。DoH 开着时也会跟着一起问。
        val (fetched, systemMs, savedPinnedMs) = coroutineScope {
            val bootstrap = async { fetchBootstrap() }
            val doh = async { dohAddressesIfEnabled(preferences, savedIps) }
            val system = async { measure(PicaLine.System, emptyList()) }
            val pinned = async { if (savedIps.isEmpty()) null else measure(PicaLine.PinnedFast, savedIps) }
            Triple(
                (doh.await() + bootstrap.await()?.addresses.orEmpty()).distinct(),
                system.await(),
                pinned.await(),
            )
        }

        val poolChanged = fetched.isNotEmpty() && fetched != savedIps
        ips = fetched.ifEmpty { savedIps }

        // 首次启动（或服务端换了 IP 池）才需要用新池补测一次，平时直接复用并行结果。
        val pinnedMs = when {
            ips.isEmpty() -> null
            !poolChanged -> savedPinnedMs
            else -> measure(PicaLine.PinnedFast, ips)
        }

        probes = listOf(
            LineProbe(PicaLine.System, systemMs, done = true),
            LineProbe(PicaLine.PinnedFast, pinnedMs, done = true),
            LineProbe(PicaLine.PinnedAlt, pinnedMs, done = true),
        )

        val candidates = listOfNotNull(
            systemMs?.let { PicaLine.System to it },
            pinnedMs?.let { PicaLine.PinnedFast to it },
        )
        val pick = candidates.minByOrNull { it.second }?.first ?: PicaLine.System
        note = when {
            candidates.isEmpty() -> "两条线路都没通，先用线路 1 试试。"
            ips.isEmpty() -> "没拿到下发 IP，只剩系统解析可用。"
            else -> null
        }

        applyPicaRoute(pick, ips)
        // 把结果交给后台管家：之后哪条线路开始连不上，它知道该换成哪条。
        LineRouter.adopt(pick, candidates, ips)
        chosen = pick
        loading = false
    }

    /** 用户手动点了一条：立刻按它重建客户端，不再等自动选。 */
    suspend fun choose(line: PicaLine) {
        if (chosen == line) return
        applyPicaRoute(line, ips)
        store.save(line, ips, probes.firstOrNull { it.line == line }?.latencyMs)
        chosen = line
        loading = false
    }

    /** DoH 打开时「系统解析」实际走 DoH 引擎，测速按真实那条路来测。 */
    private suspend fun measure(line: PicaLine, ips: List<String>): Long? {
        val engine = when {
            line.pinnedDns -> runCatching { pinnedEngine(ips) }.getOrNull()
            preferences.doh -> dohEngine()
            else -> null
        }
        val client = if (engine != null) HttpClient(engine) { probe() } else HttpClient { probe() }
        return try {
            val mark = TimeSource.Monotonic.markNow()
            // 只要连得上、有响应就行，状态码无所谓（没带签名就是 4xx）。
            client.get("https://picaapi.picacomic.com/")
            mark.elapsedNow().inWholeMilliseconds
        } catch (t: Throwable) {
            null
        } finally {
            client.close()
        }
    }

    private fun HttpClientConfig<*>.probe() {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 4_000
            connectTimeoutMillis = 3_500
        }
    }
}
