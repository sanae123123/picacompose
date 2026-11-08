package com.picacompose.ui.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.picacompose.picaapi.model.Paged

/**
 * 分页列表状态：一次只要一页，滚到底再续。
 *
 * 用法：
 * - `val pager = remember { Pager { page -> api.list(page) } }`
 * - 首屏 `LaunchedEffect(pager) { pager.loadNext() }`
 * - 页面在滚动接近底部时再调一次 [loadNext]，[loading] 挡住并发重复请求。
 */
@Stable
class Pager<T>(
    private val load: suspend (page: Int) -> Paged<T>,
) {
    var items by mutableStateOf<List<T>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loadedPage by mutableStateOf(0)
        private set
    var lastPage by mutableStateOf(0)
        private set

    /** 还有下一页可拉（第一页没拉过算 true）。 */
    val hasMore: Boolean get() = loadedPage == 0 || loadedPage < lastPage

    suspend fun loadNext() {
        if (loading) return
        if (loadedPage > 0 && loadedPage >= lastPage) return
        loading = true
        error = null
        runCatching { load(loadedPage + 1) }
            .onSuccess { paged ->
                items = items + paged.docs
                loadedPage = paged.page.takeIf { it > 0 } ?: (loadedPage + 1)
                lastPage = paged.pages.coerceAtLeast(loadedPage)
            }
            .onFailure { error = it.message ?: "列表没拉到" }
        loading = false
    }

    /** 续页失败后的重试：清掉错误再拉同一页。 */
    suspend fun retry() = loadNext()

    /**
     * 重拉第一页并替换掉现有内容，列表不闪空。
     *
     * 给「关掉页面再进来」用：点赞状态、回复数这种会变的东西以服务端为准，重新拉一遍就行。
     */
    suspend fun reloadFirstPage() {
        if (loading) return
        loading = true
        error = null
        runCatching { load(1) }
            .onSuccess { paged ->
                items = paged.docs
                loadedPage = 1
                lastPage = paged.pages.coerceAtLeast(1)
            }
            .onFailure { error = it.message ?: "列表没拉到" }
        loading = false
    }

    /** 整个列表从头再来（下拉刷新 / 切线路后）。 */
    suspend fun refresh() {
        if (loading) return
        items = emptyList()
        loadedPage = 0
        lastPage = 0
        error = null
        loadNext()
    }
}
