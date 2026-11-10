package com.picacompose.data

import com.picacompose.picaapi.ImageIps
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

/** 后台测速列表里的一条：`latencyMs == null` 就是这条测下来不通。 */
data class LineStatus(
    val line: PicaLine,
    val latencyMs: Long?,
)

/** 池子里最多连续轮几台；轮完还不通，才回去测线路 / 换线路。 */
private const val MAX_POOL_ROTATIONS = 3

/** 后台测速的产物。 */
private data class ProbeResult(
    val statuses: List<LineStatus>,
    val ips: List<String>,
)

/**
 * 后台线路管家。
 *
 * 上次选过线路就直接拿它进主界面，测速挪到后台慢慢做完；主界面里任何一个请求在传输层失败
 * （连不上、超时、DNS 挂了），就立刻换成后台测出来能通的那条，不用等用户去设置页手动换。
 *
 * 线路 2 / 3 共用一批下发 IP，所以只测「系统解析」和「下发 IP」两个点，结果一起记进后台列表。
 * 每次换完线路都会重测一遍，列表里不通的线路会被标出来 —— 后面再故障也不会往不通的线上换。
 */
object LineRouter {

    /** 后台测过的每一条，没测过的线路不在里面。 */
    @Volatile
    private var statusList: List<LineStatus> = emptyList()

    /** 后台列表的快照，设置页拿它显示每条线路测出来多少。 */
    val statuses: List<LineStatus> get() = statusList

    /** 后台列表里写得通的那几条，按快到慢。不通的（延迟为 null）在这里面永远不出现。 */
    private val ranked: List<Pair<PicaLine, Long>>
        get() = statusList.mapNotNull { status -> status.latencyMs?.let { status.line to it } }
            .sortedBy { it.second }

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

    /** 这次故障里已经在池子里轮过几台了（换到线路那一步会清零）。 */
    private var poolRotations = 0

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
        if (!probe) {
            // 自动测速关着也得把 DoH 查一次：不然开关坏了会一直坏着，每次启动都白等一遍。
            scope.launch { dohAddressesIfEnabled(preferences, ips) }
            return
        }
        if (probeJob?.isActive == true) return
        // 手上已经有一份可用的优选池：不测速、也不自己换线路了。
        // 接口和图片都从池子里取地址，出问题在池子里轮换（见 [recover]），扰动的只是地址。
        if (ImageIps.hasUsablePool) {
            poolRotations = 0
            scope.launch { dohAddressesIfEnabled(preferences, ips) }
            return
        }
        probeJob = scope.launch {
            val result = runCatching { probeAll(ips) }.getOrNull() ?: return@launch
            storeResult(result)
            // 就算现在这条连得上，也换成测出来最快的那条。
            val current = store.savedLine ?: line
            val best = ranked.firstOrNull() ?: return@launch
            if (best.first == current) return@launch
            switchTo(best.first, best.second, "已自动切到${best.first.title}（最快，${best.second} ms）")
        }
    }

    /** 把一次测速写进后台列表，顺手存盘；IP 池可能会变，设置页看到的也应该是最近一次的结果。 */
    private fun storeResult(result: ProbeResult) {
        statusList = result.statuses
        knownIps = result.ips
        val current = store.savedLine ?: appliedLine
        store.save(
            line = current,
            ips = result.ips,
            latencyMs = ranked.firstOrNull { it.first == current }?.second ?: store.savedLatencyMs,
        )
    }

    /** 线路选择页已经测过一遍，直接接管结果，免得再测一次。 */
    fun adopt(line: PicaLine, candidates: List<Pair<PicaLine, Long>>, ips: List<String>) {
        val byLine = candidates.toMap()
        // 线路 3 和线路 2 用同一批 IP，测一次就够，结果共用。
        statusList = PicaLine.entries.map { entry ->
            val latency = byLine[entry] ?: if (entry == PicaLine.PinnedAlt) byLine[PicaLine.PinnedFast] else null
            LineStatus(entry, latency)
        }
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
        // 有优选池就先在池子里换一台：不动线路、不重建客户端，下一批请求自然走新地址。
        if (ImageIps.hasUsablePool && poolRotations < MAX_POOL_ROTATIONS) {
            poolRotations += 1
            ImageIps.rotate()
            appliedIps = ImageIps.ordered()
            return true
        }
        // 先等测速收尾，再拿锁：测速自己也要切换线路，锁里等它就成了死锁。
        probeJob?.takeIf { it.isActive }?.join()
        return switchLock.withLock {
            if (lastSwitch?.let { it.elapsedNow() < 6.seconds } == true) return@withLock false

            // 池子轮完也不通，多半是换了网络：现测一遍线路再挑。
            if (ranked.isEmpty()) refreshProbes()
            poolRotations = 0

            val current = store.savedLine
            // 只从后台列表里「写得通」的线路里挑，挂掉一次就被标成不通的线路不会再拿去试。
            // 别的线路都不通时，退一步用现有 IP 池把当前这条重建一次。
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
        // 换完重测：后台列表要反映换线之后各条线路的真实状况，下次故障才不会再往一条已经
        // 不通的线上换。这次测出来的结果只记账，不再触发换线，免得来回跳。
        probeJob = scope?.launch { refreshProbes() }
        return true
    }

    /** 换完线路后的重测：只更新后台列表和存盘，不动当前线路。 */
    private suspend fun refreshProbes() {
        val result = runCatching { probeAll(knownIps) }.getOrNull() ?: return
        storeResult(result)
    }

    private suspend fun probeAll(savedIps: List<String>): ProbeResult = coroutineScope {
        // 拉列表和测速并行：系统解析不用 IP 池，先测起来。
        val bootstrap = async { fetchBootstrap() }
        // DoH 开着就多问一路：它给的地址是没被污染的那份，排在 /init 下发的地址前面。
        val doh = async { dohAddressesIfEnabled(preferences, savedIps) }
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

        // 测不出来的也记一条，标成不通，列表才对得齐三条线路。
        ProbeResult(
            statuses = listOf(
                LineStatus(PicaLine.System, system),
                LineStatus(PicaLine.PinnedFast, pinned),
                LineStatus(PicaLine.PinnedAlt, pinned),
            ).sortedBy { it.latencyMs ?: Long.MAX_VALUE },
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
