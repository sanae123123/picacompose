package com.picacompose.data

import com.picacompose.picaapi.ImageIps
import com.picacompose.picaapi.dohEngine
import com.picacompose.picaapi.fetchBootstrap
import com.picacompose.picaapi.pinnedEngine
import com.picacompose.picaapi.NetworkLog
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

    /** 降级线路在排名里加的惩罚：够把它压到任何正常线路后面。 */
    private const val DEMOTE_PENALTY_MS = 60_000L

    /** 后台测过的每一条，没测过的线路不在里面。 */
    @Volatile
    private var statusList: List<LineStatus> = emptyList()

    /** 后台列表的快照，设置页拿它显示每条线路测出来多少。 */
    val statuses: List<LineStatus> get() = statusList

    /** 后台列表里写得通的那几条，按快到慢。降级过的线路排到最后。 */
    private val ranked: List<Pair<PicaLine, Long>>
        get() = rawRanked.sortedBy { (line, ms) -> if (line == demotedLine) ms + DEMOTE_PENALTY_MS else ms }

    /** 不看待降级的原始排名：测速证明降级线路其实最快时，靠它解除降级。 */
    private val rawRanked: List<Pair<PicaLine, Long>>
        get() = statusList.mapNotNull { status -> status.latencyMs?.let { status.line to it } }
            .sortedBy { it.second }

    /**
     * 阅读器卡过一发的线路：选线时排到最后，下一轮测速证明它其实最快就自动恢复。
     *
     * 只降不换 —— 5 秒的卡顿可能只是一张图的问题，当场切线路会重建客户端、把这一屏
     * 正在下的连接全打断。先记一笔，等真要换线的时候自然避开它。
     */
    @Volatile
    private var demotedLine: PicaLine? = null

    @Volatile
    private var knownIps: List<String> = emptyList()

    /** 每成功换一次线路加一，调用方可以拿它判断刚才那次失败是不是已经被兜住了。 */
    @Volatile
    var switchCount: Int = 0
        private set

    /** 换完线路想跟用户说一句时走这里（App 里接到 toast 上）。 */
    var onNotice: ((String) -> Unit)? = null

    /**
     * 还在启动阶段（登录校验那一屏还没让位给主界面）。
     *
     * 这段时间里请求连不上、兜底也换不到一条能用的线路，就直接回线路选择页 ——
     * 启动阶段没有「稍后重试」的余地，停在哪儿都只会让人以为 App 坏了。
     */
    @Volatile
    var inStartup: Boolean = true

    /**
     * 冷启动第一条请求就连不上、兜底也没能换到一条能用的线路时叫一声。
     *
     * 这时候光在登录页给句「网络不太稳」没用 —— 用户不知道是该换线路还是该换网络。
     * App 收到就跳线路选择页，让手动挑一条（不管设置里「自动测速 / 快速进入」怎么选）。
     */
    var onStartupNoLine: (() -> Unit)? = null

    private var scope: CoroutineScope? = null
    private var probeJob: Job? = null
    private val switchLock = Mutex()
    private val store = RouteStore()
    private val preferences = LinePreferences()

    /** 这次故障里已经在池子里轮过几台了（换到线路那一步会清零）。 */
    private var poolRotations = 0

    /** 证实过超时/连不上的接口地址：建客户端时剔掉，不再拿去试。 */
    private val badIps = LinkedHashSet(store.savedBadIps)

    /** 上次拉黑的时刻：几个请求同时超时时，别一口气把整池子都标成坏的。 */
    private var lastBadMark: TimeMark? = null

    /**
     * 去掉拉黑过的地址，找建客户端的地方拿它。
     *
     * 全被拉黑说明是网络整体断了，不是某一台的问题，那就原样返回 —— 池子不能因为
     * 「全都超时过」就彻底空了。
     */
    fun usable(ips: List<String>): List<String> {
        val live = ips.filterNot { it in badIps }
        return if (live.isEmpty()) ips else live
    }

    /**
     * 把刚才失败的那台标成坏的。
     *
     * 接口钉住的地址是按顺序试的，第一个基本就是这次失败的那台；六秒内只标一次，
     * 免得几个并发请求同时超时把整池子一口气清空。
     */
    private fun markBadIp() {
        if (lastBadMark?.let { it.elapsedNow() < 6.seconds } == true) return
        val candidate = knownIps.filterNot { it in badIps }.firstOrNull() ?: return
        // 只剩最后一台没标记就是它了：全标完池子会空，说明该清掉重来。
        if (badIps.size + 1 >= knownIps.size) {
            badIps.clear()
        } else {
            badIps.add(candidate)
        }
        lastBadMark = TimeSource.Monotonic.markNow()
        store.saveBadIps(badIps)
    }

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
        appliedIps = usable(ips)
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
        // 新一轮测速说它又是最快的：那上次卡顿大概不是线路的错，降级解除。
        demotedLine?.let { demoted ->
            if (rawRanked.firstOrNull()?.first == demoted) demotedLine = null
        }
        val current = store.savedLine ?: appliedLine
        store.save(
            line = current,
            ips = result.ips,
            latencyMs = ranked.firstOrNull { it.first == current }?.second ?: store.savedLatencyMs,
        )
    }

    /**
     * 阅读器第一次 5 秒没拉到图时叫一声：把当前线路降级。
     *
     * 只改排名、不动客户端 —— 这一屏继续用地址池磨，真到了要换线路那一步（或者下一次
     * 故障兜底）会优先挑别的线路。幂等，一屏图同时卡住好几张也只会记一笔。
     */
    fun demote() {
        val line = appliedLine ?: store.savedLine ?: return
        demotedLine = line
        NetworkLog.event("线路", "降级 ${line.title}")
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
        appliedIps = usable(ips)
        store.save(line, ips, candidates.toMap()[line])
    }

    /**
     * 设置里把「自动测速」关掉时立刻调一次：停掉正在跑的测速，之后的故障兜底也不再测线 / 换线。
     *
     * 开关本身是下次冷启动才读的，但「别测了」这件事不用等重启。
     */
    fun stop() {
        probeJob?.cancel()
        probeJob = null
    }

    /** 传输层失败时调（由 [picaNetworkFailureHandler] 转进来）。 */
    fun onNetworkFailure() {
        val scope = scope ?: return
        NetworkLog.event("接口", "传输失败，进兜底")
        // 先把这台记成坏的：下面的 recover 才会用剩下的地址重建。
        markBadIp()
        scope.launch {
            val before = switchCount
            val recovered = recover()
            // 换成了一条新的（或者挪了地址）就还有指望，交给下一次请求去试；
            // 一次都没换成、又还在启动阶段，那就是没有线路可用，回选择页。
            if (!recovered && switchCount == before && inStartup) onStartupNoLine?.invoke()
        }
    }

    /**
     * 换成后台测出来最快、且不是当前这条的线路。
     *
     * [forceSwitch] 是真的要换条线路了：阅读器整整卡了一轮（5 秒 + 30 秒）才走到这儿，
     * 再在原地址池里挪一台没意义 —— 直接跳过池内轮换去测线换线。换成了会弹一句
     * 「连不上，已切到线路 X」，等那么久总得让用户知道发生了什么。
     *
     * 测速还没跑完就先等它（最多一个超时周期）；没有更好的选择时返回 false。
     */
    suspend fun recover(forceSwitch: Boolean = false): Boolean {
        // 「自动测速」关着：不测线路、也不换线路，最多在现有地址里挪一台。
        if (!preferences.autoProbe) {
            if (ImageIps.ordered().size <= 1) return false
            ImageIps.rotate()
            return true
        }
        // 接口地址里有被拉黑的：先用剩下的原地重建客户端，线路不动。
        if (!forceSwitch) {
            val line = appliedLine ?: store.savedLine
            if (line != null && rebuildOn(line)) {
                poolRotations += 1
                return true
            }
        }
        // 有优选池就先在池子里换一台：不动线路、不重建客户端，下一批请求自然走新地址。
        if (!forceSwitch && ImageIps.hasUsablePool && poolRotations < MAX_POOL_ROTATIONS) {
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

    /** 只用剩下的地址把客户端重建一次：线路不动，只换接口地址。 */
    private suspend fun rebuildOn(line: PicaLine): Boolean = switchLock.withLock {
        val live = usable(knownIps)
        if (live == appliedIps) return@withLock false
        if (runCatching { applyPicaRoute(line, live) }.isFailure) return@withLock false
        appliedLine = line
        appliedIps = live
        lastSwitch = TimeSource.Monotonic.markNow()
        true
    }

    /** 换线路 + 弹提示；拿锁的版本给协程用，`*Locked` 给已经持锁的调用方用。 */
    private suspend fun switchTo(line: PicaLine, latencyMs: Long?, note: String): Boolean =
        switchLock.withLock { switchToLocked(line, latencyMs, note) }

    private suspend fun switchToLocked(line: PicaLine, latencyMs: Long?, note: String): Boolean {
        val live = usable(knownIps)
        if (line == appliedLine && live == appliedIps) return false
        if (runCatching { applyPicaRoute(line, live) }.isFailure) return false

        store.save(line, knownIps, latencyMs ?: store.savedLatencyMs)
        appliedLine = line
        appliedIps = live
        lastSwitch = TimeSource.Monotonic.markNow()
        // 已经换走了，降级标记没用了。
        demotedLine = null
        switchCount += 1
        NetworkLog.event("线路", note)
        onNotice?.invoke(note)
        // 换完重测：后台列表要反映换线之后各条线路的真实状况，下次故障才不会再往一条已经
        // 不通的线上换。这次测出来的结果只记账，不再触发换线，免得来回跳。
        probeJob = scope?.launch { refreshProbes() }
        return true
    }

    /** 换完线路后的重测：只更新后台列表和存盘，不动当前线路。 */
    private suspend fun refreshProbes() {
        if (!preferences.autoProbe) return
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
