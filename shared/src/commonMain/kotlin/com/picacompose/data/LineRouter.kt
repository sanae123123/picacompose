package com.picacompose.data

import com.picacompose.picaapi.dohEngine
import com.picacompose.picaapi.fetchBootstrap
import com.picacompose.picaapi.pinnedEngine
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** 后台测速的产物。 */
private data class ProbeResult(
    val ranked: List<Pair<PicaLine, Long>>,
    val ips: List<String>,
)

/**
 * 后台线路管家。
 *
 * 上次选过线路就直接拿它进主界面，测速挪到后台慢慢做完；主界面里任何一个请求在传输层失败
 * （连不上、超时、DNS 挂了），就立刻换成后台测出来能通的那条，不用等用户去设置页手动换。
 *
 * 线路 2 / 3 共用一批下发 IP，所以只测「系统解析」和「下发 IP」两个点。
 */
object LineRouter {

    /** 后台测完才有值，按快到慢排。 */
    @Volatile
    private var ranked: List<Pair<PicaLine, Long>> = emptyList()

    @Volatile
    private var knownIps: List<String> = emptyList()

    /** 每成功换一次线路加一，调用方可以拿它判断刚才那次失败是不是已经被兜住了。 */
    @Volatile
    var switchCount: Int = 0
        private set

    /** 换完线路想跟用户说一句时走这里（App 里接到 toast 上）。 */
    var onNotice: ((String) -> Unit)? = null

    private var scope: CoroutineScope? = null
    private var probeJob: Job? = null
    private val switchLock = Mutex()
    private val store = RouteStore()
    private val preferences = LinePreferences()

    /** 当前客户端是按哪条线路、哪批 IP 建的，用来避免拿一模一样的组合反复重建。 */
    private var appliedLine: PicaLine? = null
    private var appliedIps: List<String> = emptyList()

    /** 上一次换线路的时刻：真断网时所有线路都不通，别让两条线路来回跳。 */
    private var lastSwitch: TimeMark? = null

    /**
     * 复用上次线路进主界面时调一次：测速放到后台跑，不挡启动。
     *
     * [probe] 传 false 就是设置里关掉了「自动测速」：只挂上故障兜底（连不上时用现有 IP 池
     * 把当前线路重建一次），不测速，也不会自己换到别的线路上。
     */
    fun start(
        scope: CoroutineScope,
        line: PicaLine,
        ips: List<String>,
        probe: Boolean = true,
    ) {
        this.scope = scope
        knownIps = ips
        appliedLine = line
        appliedIps = ips
        if (!probe) return
        if (probeJob?.isActive == true) return
        probeJob = scope.launch {
            val result = runCatching { probeAll(ips) }.getOrNull() ?: return@launch
            ranked = result.ranked
            knownIps = result.ips
            // 顺手写回：IP 池可能会变，设置页看到的也应该是最近一次的结果。
            val current = store.savedLine ?: line
            store.save(
                line = current,
                ips = result.ips,
                latencyMs = result.ranked.firstOrNull { it.first == current }?.second
                    ?: store.savedLatencyMs,
            )
            // 就算现在这条连得上，也换成测出来最快的那条。
            val best = result.ranked.firstOrNull() ?: return@launch
            if (best.first == current) return@launch
            switchTo(best.first, best.second, "已自动切到${best.first.title}（最快，${best.second} ms）")
        }
    }

    /** 线路选择页已经测过一遍，直接接管结果，免得再测一次。 */
    fun adopt(line: PicaLine, candidates: List<Pair<PicaLine, Long>>, ips: List<String>) {
        ranked = candidates.sortedBy { it.second }
        knownIps = ips
        appliedLine = line
        appliedIps = ips
        store.save(line, ips, candidates.toMap()[line])
    }

    /** 传输层失败时调（由 [picaNetworkFailureHandler] 转进来）。 */
    fun onNetworkFailure() {
        val scope = scope ?: return
        scope.launch { recover() }
    }

    /**
     * 换成后台测出来最快、且不是当前这条的线路。
     *
     * 测速还没跑完就先等它（最多一个超时周期）；没有更好的选择时返回 false。
     */
    suspend fun recover(): Boolean {
        // 先等测速收尾，再拿锁：测速自己也要切换线路，锁里等它就成了死锁。
        probeJob?.takeIf { it.isActive }?.join()
        return switchLock.withLock {
            if (lastSwitch?.let { it.elapsedNow() < 6.seconds } == true) return@withLock false

            val current = store.savedLine
            // 优先换一条别的线路；只有当前这条能通时，退一步用刚拿到的 IP 池把它重建一次。
            val target = ranked.firstOrNull { it.first != current }?.first
                ?: current
                ?: ranked.firstOrNull()?.first
                ?: return@withLock false
            switchToLocked(
                line = target,
                latencyMs = ranked.firstOrNull { it.first == target }?.second,
                note = "连不上，已切到${target.title}",
            )
        }
    }

    /** 换线路 + 弹提示；拿锁的版本给协程用，`*Locked` 给已经持锁的调用方用。 */
    private suspend fun switchTo(line: PicaLine, latencyMs: Long?, note: String): Boolean =
        switchLock.withLock { switchToLocked(line, latencyMs, note) }

    private suspend fun switchToLocked(line: PicaLine, latencyMs: Long?, note: String): Boolean {
        if (line == appliedLine && knownIps == appliedIps) return false
        if (runCatching { applyPicaRoute(line, knownIps) }.isFailure) return false

        store.save(line, knownIps, latencyMs ?: store.savedLatencyMs)
        appliedLine = line
        appliedIps = knownIps
        lastSwitch = TimeSource.Monotonic.markNow()
        switchCount += 1
        onNotice?.invoke(note)
        return true
    }

    private suspend fun probeAll(savedIps: List<String>): ProbeResult = coroutineScope {
        // 拉列表和测速并行：系统解析不用 IP 池，先测起来。
        val bootstrap = async { fetchBootstrap() }
        // DoH 开着就多问一路：它给的地址是没被污染的那份，排在 /init 下发的地址前面。
        val doh = async { dohAddressesIfEnabled(preferences) }
        val systemJob = async { measure(null) }
        val savedPinJob = async { if (savedIps.isEmpty()) null else measure(savedIps) }

        val info = bootstrap.await()
        val fetched = (doh.await() + info?.addresses.orEmpty()).distinct()
        val ips = fetched.ifEmpty { savedIps }
        val system = systemJob.await()
        val savedPin = savedPinJob.await()
        val pinned = when {
            ips.isEmpty() -> null
            fetched.isEmpty() || fetched == savedIps -> savedPin
            else -> measure(ips)
        }

        ProbeResult(
            ranked = listOfNotNull(
                system?.let { PicaLine.System to it },
                pinned?.let { PicaLine.PinnedFast to it },
            ).sortedBy { it.second },
            ips = ips,
        )
    }

    /**
     * 测一次延迟；`ips` 为 null 表示走系统解析。只要连得上有响应就算通，状态码无所谓。
     *
     * DoH 打开时「系统解析」这条实际用的是 DoH 引擎，测速也得按它来测，不然测出来的不是
     * 待会儿真正用的那条路。
     */
    private suspend fun measure(ips: List<String>?): Long? {
        val engine = when {
            ips != null -> runCatching { pinnedEngine(ips) }.getOrNull()
            preferences.doh -> dohEngine()
            else -> null
        }
        val client = if (engine != null) HttpClient(engine) { probe() } else HttpClient { probe() }
        return try {
            val mark = TimeSource.Monotonic.markNow()
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
