package com.picacompose.data

import coil3.intercept.Interceptor
import coil3.network.HttpException
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.request.SuccessResult
import kotlinx.coroutines.delay

/**
 * 图片加载失败时再打一枪。
 *
 * Coil 默认失败就是失败，界面上直接留一块灰底。移动网络抖一下、图床刚好在换节点都会撞上，
 * 所以隔 350 ms 用同一个地址再请求一次；4xx 不重试 —— 那是「这张图本来就不在这个地址」，
 * 重试只会白费一次流量。真的还打不开就把原来的错误原样交回去。
 *
 * 重试只救得了偶发的失败；如果图床域名根本解析不出来（DNS 被污染），要靠设置里的
 * DoH 开关，换地址没用。
 */
internal class ImageRetryInterceptor : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val result = chain.proceed()
        if (result is SuccessResult) return result
        val error = result as? ErrorResult ?: return result
        val code = (error.throwable as? HttpException)?.response?.code
        if (code != null && code in 400..499) return error
        delay(RETRY_DELAY_MS)
        // 同一个地址再请求一次：请求链本身是只读的，第二次 proceed 等于重新下一单。
        return runCatching { chain.withRequest(chain.request.newBuilder().build()).proceed() }
            .getOrDefault(error)
    }

    private companion object {
        const val RETRY_DELAY_MS = 350L
    }
}
