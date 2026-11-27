package com.picacompose.data

import coil3.intercept.Interceptor
import coil3.network.HttpException
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.size.Dimension
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * 图片加载失败时再打一枪。
 *
 * Coil 默认失败就是失败，界面上直接留一块灰底。移动网络抖一下、图床刚好在换节点都会撞上，
 * 所以隔 350 ms 用同一个地址再请求一次；4xx 不重试 —— 那是「这张图本来就不在这个地址」，
 * 重试只会白费一次流量。真的还打不开就把原来的错误原样交回去。
 *
 * 重试只救得了偶发的失败；如果图床域名根本解析不出来（DNS 被污染），要靠设置里的
 * DoH 开关，换地址没用。
 *
 * 顺带做图床兜底：官方拼图地址有两套等价写法（`utils.g.b`），`/init` 下发的 imageServer
 * 一套、`fileServer + /static/` 一套。我们平时走后者，所以同一张图还能从 imageServer
 * 那台图床上取一次，主图床挂了不至于整页灰。
 *
 * 更麻烦的一种失败是「不报错也不返回」：连接建起来了、图床那头一直不回，界面就永远停在
 * Loading。网络栈自己的超时是几十秒起步，等它等于让用户干瞪眼，所以先给 9 秒的判断窗口，
 * 超了就当成这条路不通 —— 换图床地址（必要时连线路一起换）再拉一次。
 *
 * 阅读器另算一套：一页原图卡住时后面的页也跟着排队，等 9 秒太久。所以 5 秒没影就
 * 五路并发拉同一张，再 30 秒还没赢就换线路、并让后台重测 IP 池（见 [raceReader]）。
 */
internal class ImageRetryInterceptor : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        // 开一条「开始」的日志：请求卡住时后面不会再有收尾日志，一对日志就能看出卡在哪张图、
        // 卡在哪个域名上 —— 光看界面只有转圈，分不清是解析、连接还是图床不返回。
        val url = chain.request.data as? String
        url?.let { log("start ${chain.size} $it") }
        val start = TimeSource.Monotonic.markNow()

        // 网络图才谈得上「卡住」；离线包里取图是本机读 zip，慢了也不是线路的问题。
        if (url != null && url.startsWith("http")) {
            if (isReaderPage(chain)) {
                // 阅读器：一页一页往下翻，卡一页就卡住整个阅读节奏，等不起 9 秒。
                val first = withTimeoutOrNull(READER_STALL_MS) { chain.proceed() }
                if (first != null) return settle(chain, first, start)
                log("reader stall ${READER_STALL_MS}ms $url")
                // 5 秒没影先给这条线路记一笔降级：不立刻切（会重建客户端、打断这一屏），
                // 但真到了要换线路那一步会优先挑别的线路。
                LineRouter.demote()
                raceReader(chain)?.let { return settle(chain, it, start) }
                // 5 秒 + 30 秒还没影：多半不是单张图的事，而是这条线路 / 池子里的地址不行了。
                // 换线路（会弹一句「连不上，已切到线路 X」），同时叫后台重测一轮；
                // 最后这趟不再限时，慢图该等它下完。
                log("reader stuck ${READER_STALL_MS + READER_RACE_WINDOW_MS}ms $url")
                StallSwitch.recover(force = true)
                requestImageIpsRefresh()
            } else {
                val first = withTimeoutOrNull(STALL_WINDOW_MS) { chain.proceed() }
                if (first != null) return settle(chain, first, start)
                log("stall ${STALL_WINDOW_MS}ms $url")
                StallSwitch.recover()
                // 换完路不再限时：真慢的图该等它下完，一直掐断就永远看不到。
                // 超时那一刻这条请求的 call 已经取消了，重拉会重新解析地址，新的顺序才真的生效。
                attempt(chain) { chain.request }?.let { return settle(chain, it, start) }
            }
        }
        return settle(chain, chain.proceed(), start)
    }

    /**
     * 这一条是不是阅读器的整页原图。
     *
     * 阅读器请求的是 `Size.ORIGINAL`（宽高都没指定，是 [Dimension.Undefined]），
     * 列表、封面那些都是明确的小尺寸，靠这个分得开；预抓也用同一套参数，所以预抓页同样走快的那条路。
     */
    private fun isReaderPage(chain: Interceptor.Chain): Boolean {
        val width = chain.size.width
        if (width is Dimension.Undefined) return true
        return width is Dimension.Pixels && width.px >= 2_000
    }

    /**
     * 五路并发拉同一张图，谁先到手用谁。
     *
     * 五路都走原地址：图床的 DNS 每开一条新连接就把池子往后挪一台（见 `ImageDns` 的
     * `turn`），所以五条连接自然铺到池子里最快的那几台机器上 —— 等于「从 IP 池里随机挑
     * 五台一起下」，谁先回来用谁，剩下四条立刻取消。全都不行才返回 null。
     */
    private suspend fun raceReader(chain: Interceptor.Chain): ImageResult? = coroutineScope {
        val hits = Channel<ImageResult>(Channel.UNLIMITED)
        val jobs = List(READER_RACE_COPIES) {
            launch {
                val result = runCatching {
                    chain.withRequest(chain.request).proceed()
                }.getOrNull()
                if (result is SuccessResult) hits.send(result)
            }
        }
        val winner = withTimeoutOrNull(READER_RACE_WINDOW_MS) { hits.receive() }
        jobs.forEach { it.cancel() }
        winner?.let { log("race won by ${it.request.data}") }
        winner
    }

    /** 请求有结果之后的处理：成功的记慢图日志，失败了再打一枪、再兜底另一台图床。 */
    private suspend fun settle(
        chain: Interceptor.Chain,
        result: ImageResult,
        start: TimeMark,
    ): ImageResult {
        val elapsed = start.elapsedNow().inWholeMilliseconds
        if (result is SuccessResult) {
            // 慢图不报错，但慢到十几秒基本就等着用户看转圈了，留一行日志好对照。
            if (elapsed >= SLOW_MS) log("slow ${elapsed}ms ${chain.request.data}")
            return result
        }
        val error = result as? ErrorResult ?: return result
        val url = chain.request.data as? String ?: return error
        val code = (error.throwable as? HttpException)?.response?.code
        val missing = code != null && code in 400..499

        // 404 说明这个地址本来就没有这张图，原样重试没意义；网络类失败才值得再打一枪。
        if (!missing) {
            delay(RETRY_DELAY_MS)
            val again = attempt(chain) { chain.request }
            if (again is SuccessResult) return again
        }
        // 主图床不行就换另一台：同一张图在 imageServer 那套地址上往往还是好的。
        for (candidate in alternateImageUrls(url)) {
            val alt = attempt(chain) { chain.request.newBuilder().data(candidate).build() }
            if (alt is SuccessResult) return alt
        }
        log("failed ${elapsed}ms $url -> ${error.throwable}")
        return error
    }

    private suspend inline fun attempt(
        chain: Interceptor.Chain,
        request: () -> coil3.request.ImageRequest,
    ): ImageResult? = runCatching {
        chain.withRequest(request()).proceed()
    }.getOrNull()

    private companion object {
        const val RETRY_DELAY_MS = 350L

        /** 超过这个耗时就算慢图，只记日志不重试。 */
        const val SLOW_MS = 5_000L

        /** 列表那些图挂多久算「卡住」：超了就换路重拉。 */
        const val STALL_WINDOW_MS = 9_000L

        /** 阅读器的图等多久就开始五路并发。 */
        const val READER_STALL_MS = 5_000L

        /** 五路并发再等这么久还没赢（加起来 35 秒），就当这条线路有问题，换线并让后台重测。 */
        const val READER_RACE_WINDOW_MS = 30_000L

        /** 并发几路：铺到地址池轮换的前几台机器上。 */
        const val READER_RACE_COPIES = 5
    }
}

/**
 * 卡住之后换路，一次卡顿只换一次。
 *
 * 一屏图会同时卡住好几张，如果每张都去换线路，换线路要重建客户端、重测速，几趟下来
 * 反而把本来能用的线路折腾没了。所以这里加一道 6 秒的闸：窗口内只换一次，其余几张
 * 跟着一起重新拉 —— 地址池的顺序已经变了，它们重拉时自然就走新地址。
 */
private object StallSwitch {

    /** 两道卡顿之间至少隔这么久，才允许再换一次。 */
    private val COOLDOWN = 6.seconds

    /** 阅读器 35 秒都没影时是另一回事：允许早一点换，且直接换线路不再池内挪，
     *  但也不能让一屏图连着换。 */
    private val FORCE_COOLDOWN = 3.seconds

    private val lock = Mutex()

    /** 上一次换成功的时间；第一次卡住时是 null。 */
    private var lastSwitch: TimeMark? = null

    suspend fun recover(force: Boolean = false) {
        lock.withLock {
            val last = lastSwitch
            val gate = if (force) FORCE_COOLDOWN else COOLDOWN
            if (last != null && last.elapsedNow() < gate) return
            val switched = runCatching { LineRouter.recover(forceSwitch = force) }.getOrDefault(false)
            if (switched) {
                lastSwitch = TimeSource.Monotonic.markNow()
                log("routed away after stall")
            }
        }
    }
}

/**
 * 同一张图的备用地址。
 *
 * 官方拼图逻辑里 imageServer 那套地址自带静态目录（形如 `https://xxx/static`），
 * 而 `fileServer + "/static/" + path` 是另一套写法，两者指向的图床可以互换。
 * 拼不出来就不猜，返回空列表。
 */
internal fun alternateImageUrls(url: String): List<String> {
    val marker = "/static/"
    val at = url.indexOf(marker)
    if (at <= 0) return emptyList()
    val server = pica.currentImageServer?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return emptyList()
    if (url.startsWith(server)) return emptyList()
    val tail = url.substring(at + marker.length)
    if (tail.isEmpty()) return emptyList()
    val base = if (server.contains("/static")) server else "$server/static"
    return listOf("$base/$tail")
}

/** 只在 logcat 里留痕：图片这条链路出问题时，光看界面分不清是解析、连接还是解码卡住。 */
private fun log(message: String) {
    println("PicaImage: $message")
}
