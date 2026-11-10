package com.picacompose.ui.home

import kotlinx.coroutines.CancellationException

/**
 * 拉数据版的 `runCatching`：协程被取消时原样抛出去。
 *
 * 页面切走、整页被销毁时 `LaunchedEffect` 会把协程取消。普通 `runCatching` 把
 * `CancellationException` 当成加载失败，于是界面上就冒出那句
 * 「The coroutine scope left the composition」，还挡住重新加载。
 */
internal inline fun <T> catchingLoad(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
