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
    /**
     * 用来去重的键（一般是 `id`）。
     *
     * 服务端分页在列表有新增时会错位，同一项可能出现在两页里；带上这个就不会
     * 让同一个 key 塞进 LazyColumn，那条路是会直接崩的。传 null 就原样拼接。
     */
    private val itemKey: ((T) -> String)? = null,
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

    /** 服务端给的总条数（`total`），列表头显示「共 N 本」用。 */
    var total by mutableStateOf(0)
        private set

    /** 还有下一页可拉（第一页没拉过算 true）。 */
    val hasMore: Boolean get() = loadedPage == 0 || loadedPage < lastPage

    suspend fun loadNext() {
        if (loading) return
        if (loadedPage > 0 && loadedPage >= lastPage) return
        loading = true
        error = null
        try {
            catchingLoad { load(loadedPage + 1) }
                .onSuccess { paged ->
                    items = merged(items + paged.docs)
                    loadedPage = paged.page.takeIf { it > 0 } ?: (loadedPage + 1)
                    lastPage = paged.pages.coerceAtLeast(loadedPage)
                    total = paged.total
                }
                .onFailure { error = it.message ?: "列表没拉到" }
        } finally {
            // 页面被销毁导致取消时也得放掉，不然下次进来一直被挡住。
            loading = false
        }
    }

    /** 按 [itemKey] 去重，保留先出现的那条；没配 key 就原样返回。 */
    private fun merged(list: List<T>): List<T> {
        val key = itemKey ?: return list
        val seen = HashSet<String>(list.size)
        return list.filter { seen.add(key(it)) }
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
        try {
            catchingLoad { load(1) }
                .onSuccess { paged ->
                    items = merged(paged.docs)
                    loadedPage = 1
                    lastPage = paged.pages.coerceAtLeast(1)
                }
                .onFailure { error = it.message ?: "列表没拉到" }
        } finally {
            loading = false
        }
    }

    /**
     * 直接跳到第 [page] 页：把现有内容换成那一页，后面接着往 [page] + 1 续。
     *
     * 评论区的「跳页」用它；页码越界或正在拉的时候就当没点。
     */
    suspend fun jumpTo(page: Int) {
        if (loading || page < 1) return
        loading = true
        error = null
        try {
            catchingLoad { load(page) }
                .onSuccess { paged ->
                    items = merged(paged.docs)
                    loadedPage = page
                    lastPage = paged.pages.coerceAtLeast(page)
                }
                .onFailure { error = it.message ?: "那一页没拉到" }
        } finally {
            loading = false
        }
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
