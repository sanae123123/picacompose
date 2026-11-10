package com.picacompose.data

import coil3.intercept.Interceptor
import coil3.network.HttpException
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.request.SuccessResult
import kotlinx.coroutines.delay
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
 */
internal class ImageRetryInterceptor : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        // 开一条「开始」的日志：请求卡住时后面不会再有收尾日志，一对日志就能看出卡在哪张图、
        // 卡在哪个域名上 —— 光看界面只有转圈，分不清是解析、连接还是图床不返回。
        (chain.request.data as? String)?.let { log("start ${chain.size} $it") }
        val start = TimeSource.Monotonic.markNow()
        val result = chain.proceed()
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
