package com.picacompose.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.picacompose.data.pica
import com.picacompose.picaapi.model.ComicSummary
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 搜索页的状态：输入框里的字、已提交的关键词、排序、结果分页、热搜词。
 *
 * 放在 [HomeShell] 里，退出搜索页去翻详情、再退回来时结果和滚动位置都还在。
 */
@Stable
class SearchState {
    /** 输入框里正在打的字。 */
    var query by mutableStateOf("")
        private set

    /** 已经搜过的关键词，空表示还没搜过。 */
    var keyword by mutableStateOf("")
        private set

    /** 排序方式，null 就是服务端原来的顺序（和本子列表那套一样）。 */
    var sort by mutableStateOf<String?>(null)
        private set

    /** 完结筛选。搜索接口没有这个参数，只能拉到结果后在本地筛（见 [pagerFor]）。 */
    var finished by mutableStateOf(ComicFinished.All)
        private set

    /** 当前关键词的结果分页；null 是还没搜。 */
    var pager by mutableStateOf<Pager<ComicSummary>?>(null)
        private set

    /** 服务端下发的热搜词，搜之前先摆一排。 */
    var hotKeywords by mutableStateOf<List<String>>(emptyList())
        private set

    private var hotStarted = false

    /** 进来时要不要自动弹键盘：只有从分类页那颗搜索按钮点开才算。 */
    private var focusPending = false

    fun onQueryChange(text: String) {
        query = text
    }

    /** 从分类页点搜索进来：下一次进搜索页时把光标放进输入框。 */
    fun requestFocus() {
        focusPending = true
    }

    /** 取走「要弹键盘」这个待办，取过一次就清掉。 */
    fun consumeFocusRequest(): Boolean {
        val pending = focusPending
        focusPending = false
        return pending
    }

    /** 提交搜索：同一个词再点一次就当没点，免得白拉一遍。 */
    fun submit(text: String = query) {
        val next = text.trim()
        if (next.isEmpty()) return
        query = next
        if (next == keyword && pager != null) return
        keyword = next
        pager = pagerFor(next, sort)
    }

    /** 换排序：同一个关键词按新顺序重搜一遍，没搜过就只记下来。 */
    fun onSortChange(value: String?) {
        if (value == sort) return
        sort = value
        if (keyword.isBlank()) return
        pager = pagerFor(keyword, value)
    }

    /** 关掉搜索页时清空：词、排序、结果都回到刚进来的样子，热搜词留着不用重拉。 */
    fun reset() {
        query = ""
        keyword = ""
        sort = null
        finished = ComicFinished.All
        pager = null
        focusPending = false
    }

    /** 换排序：同一个关键词按新顺序重搜一遍，没搜过就只记下来。 */
    fun onFinishedChange(value: ComicFinished) {
        if (value == finished) return
        finished = value
        if (keyword.isBlank()) return
        pager = pagerFor(keyword, sort)
    }

    /**
     * 一次搜索的分页。
     *
     * 搜索接口不吃完结参数，所以等服务端回来后在 `docs` 上再筛一遍；页数、总数仍按
     * 服务端那套走，翻页不会乱（某一页被筛空时由界面自己接着往下翻）。
     */
    private fun pagerFor(keyword: String, sort: String?): Pager<ComicSummary> {
        val want = finished.value
        return Pager(
            load = { page ->
                val paged = pica.comics.advancedSearch(keyword = keyword, sort = sort, page = page)
                if (want == null) {
                    paged
                } else {
                    paged.copy(docs = paged.docs.filter { it.finished == want })
                }
            },
            itemKey = { it.id },
        )
    }

    /** 热搜词只问一次，没拿到就不摆那一块，不挡搜索。 */
    suspend fun ensureHotKeywords() {
        if (hotStarted) return
        hotStarted = true
        hotKeywords = catchingLoad { pica.meta.keywords() }.getOrDefault(emptyList())
    }
}

@Composable
fun rememberSearchState(): SearchState = remember { SearchState() }

/**
 * 搜索页：整页容器和本子列表一样（[PageScaffold]），顶栏也会跟着滚动自动收起 / 展开。
 *
 * 顶栏还是那套：返回 / 标题 / 共几本 / 汉堡（跳页 + 排序方式），下面多一行搜索框。
 * 没搜的时候摆一排热搜词，点一个直接搜；结果沿用分类列表那一行本子，滚到底接着续页。
 */
@Composable
fun SearchContent(
    state: SearchState = rememberSearchState(),
    listState: LazyListState = rememberLazyListState(),
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** 点一本就打开详情页；第二个参数是这一行的变形 key。 */
    onOpenComic: (ComicSummary, String) -> Unit = { _, _ -> },
    comicRowModifier: @Composable (String) -> Modifier = { Modifier },
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var menuOpen by remember { mutableStateOf(false) }
    var showJump by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    val pager = state.pager

    LaunchedEffect(state) { state.ensureHotKeywords() }
    // 等整页淡进来再弹键盘，不然跟变形动画挤在一起。
    // 从本子详情退回搜索页时不弹：那时候要接着看刚才的结果，不该再顶一层键盘上来。
    LaunchedEffect(Unit) {
        if (!state.consumeFocusRequest()) return@LaunchedEffect
        delay(240)
        runCatching { focus.requestFocus() }
    }

    // 换关键词 / 换排序就回顶部、拉第一页；同一个 pager 不重拉。
    LaunchedEffect(pager) {
        if (pager == null) return@LaunchedEffect
        listState.scrollToItem(0)
        if (pager.items.isEmpty()) pager.loadNext()
    }

    // 滚到离底部还剩几条就续页。
    LaunchedEffect(listState, pager) {
        if (pager == null) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (total > 0 && lastVisible >= total - 5 && pager.hasMore && pager.error == null) {
                    pager.loadNext()
                }
            }
    }

    // 本地过滤可能把整整一页都筛掉：这时列表是空的、也滚不动，得自己接着翻下一页，
    // 不然会停在「没搜到」上，其实后面还有。
    LaunchedEffect(listState, pager) {
        if (pager == null) return@LaunchedEffect
        snapshotFlow { pager.items.isEmpty() to (pager.hasMore && !pager.loading && pager.error == null) }
            .distinctUntilChanged()
            .collect { (empty, canLoad) ->
                if (empty && canLoad) pager.loadNext()
            }
    }

    val submit: (String?) -> Unit = { text ->
        state.submit(text ?: state.query)
        keyboard?.hide()
    }
    // 下拉刷新 = 拿同一个词重搜一遍（和本子列表的刷新一个意思）。
    val refresh: (suspend () -> Unit)? = if (pager == null) null else ({ pager.reloadFirstPage() })

    PageScaffold(
        state = listState,
        modifier = modifier.fillMaxSize().imePadding(),
        onRefresh = refresh,
        header = {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        PicaGlyph(
                            icon = PicaIcon.Back,
                            modifier = Modifier.size(22.dp),
                            tint = colors.onBackground,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.weight(1f)) {
                        PageHeader(title = "搜索")
                    }
                    if (pager != null && pager.total > 0) {
                        Text(
                            text = "${compactCount(pager.total)} 本",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    // 筛选：排序方式 + 完结状态。完结是拿回来后在本地筛的（搜索接口不吃这个参数）。
                    IconButton(
                        onClick = { showFilter = true },
                        modifier = Modifier.size(40.dp),
                    ) {
                        PicaGlyph(
                            icon = PicaIcon.Filter,
                            modifier = Modifier.size(22.dp),
                            tint = if (state.finished == ComicFinished.All) {
                                colors.onBackground
                            } else {
                                colors.primary
                            },
                        )
                    }
                    // 汉堡菜单：跳页 + 排序方式，和本子列表里那颗一样。
                    Box {
                        IconButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier.size(40.dp),
                        ) {
                            PicaGlyph(
                                icon = PicaIcon.Menu,
                                modifier = Modifier.size(22.dp),
                                tint = colors.onBackground,
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("跳页") },
                                enabled = pager != null,
                                leadingIcon = {
                                    PicaGlyph(
                                        icon = PicaIcon.ArrowRight,
                                        modifier = Modifier.size(18.dp),
                                        tint = colors.onSurfaceVariant,
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    showJump = true
                                },
                            )
                            HorizontalDivider()
                            Text(
                                text = "排序方式",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                            )
                            ComicSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    trailingIcon = {
                                        if (option.value == state.sort) {
                                            PicaGlyph(PicaIcon.Check, Modifier.size(18.dp), colors.primary)
                                        }
                                    },
                                    onClick = {
                                        menuOpen = false
                                        state.onSortChange(option.value)
                                    },
                                )
                            }
                        }
                    }
                }
                // 搜索框：回车 / 键盘上的搜索键 / 右边那颗「搜索」都行。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = state::onQueryChange,
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        placeholder = {
                            Text("搜本子：标题、作者", style = MaterialTheme.typography.bodyMedium)
                        },
                        singleLine = true,
                        shape = CircleShape,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        leadingIcon = {
                            PicaGlyph(
                                icon = PicaIcon.Search,
                                modifier = Modifier.size(20.dp),
                                tint = colors.onSurfaceVariant,
                            )
                        },
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { state.onQueryChange("") }) {
                                    PicaGlyph(
                                        icon = PicaIcon.Close,
                                        modifier = Modifier.size(18.dp),
                                        tint = colors.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit(null) }),
                        colors = searchFieldColors(),
                    )
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { submit(null) }) {
                        Text("搜索", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
    ) {
        if (pager == null) {
            item(key = "search-hot") {
                HotKeywordPanel(state = state, onSubmit = submit)
            }
        } else {
            if (pager.items.isEmpty()) {
                item(key = "search-state") {
                    when {
                        pager.error != null -> FailedBlock(pager.error.orEmpty()) {
                            scope.launch { pager.retry() }
                        }

                        pager.loading -> LoadingBlock("正在搜…")
                        // 本地筛掉了也是这个空态，说清楚是筛选的问题还是词的问题。
                        state.finished == ComicFinished.All -> EmptyBlock("没搜到，换个词试试")
                        else -> EmptyBlock("这个筛选下没搜到，换个条件试试")
                    }
                }
            }
            items(count = pager.items.size, key = { pager.items[it].id }) { index ->
                val comic = pager.items[index]
                val rowKey = "searchComic-${comic.id}"
                ComicListRow(
                    comic = comic,
                    onClick = { onOpenComic(comic, rowKey) },
                    modifier = comicRowModifier(rowKey),
                )
            }
            if (pager.items.isNotEmpty()) {
                item(key = "search-footer") {
                    ListFooter(
                        loading = pager.loading,
                        error = pager.error,
                        hasMore = pager.hasMore,
                        onRetry = { scope.launch { pager.retry() } },
                    )
                }
            }
        }
    }

    if (showFilter) {
        ComicFilterDialog(
            sort = state.sort,
            finished = state.finished,
            onApply = { newSort, newFinished ->
                showFilter = false
                state.onSortChange(newSort)
                state.onFinishedChange(newFinished)
            },
            onDismiss = { showFilter = false },
        )
    }

    if (showJump && pager != null) {
        PagerJumpDialog(
            pager = pager,
            onDismiss = { showJump = false },
            onJumped = { scope.launch { listState.scrollToItem(0) } },
        )
    }
}

/** 还没搜时的那一块：热搜词 + 一句提示。放在列表当第一项，滚动交给外面的容器。 */
@Composable
private fun HotKeywordPanel(
    state: SearchState,
    onSubmit: (String?) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        if (state.hotKeywords.isNotEmpty()) {
            Text(
                text = "大家都在搜",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.hotKeywords.forEach { word ->
                    HotKeywordChip(text = word, onClick = { onSubmit(word) })
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        Text(
            text = "输入关键词，按键盘上的搜索键就能找本子；标题和作者都能搜。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

/** 一颗热搜词：圆角小胶囊，点一下就拿它去搜。 */
@Composable
private fun HotKeywordChip(text: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = colors.secondaryContainer.copy(alpha = 0.75f),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun searchFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    cursorColor = MaterialTheme.colorScheme.primary,
)
