package com.picacompose.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
 * 搜索页的状态：输入框里的字、已提交的关键词、结果分页、热搜词。
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
        pager = Pager(
            load = { page -> pica.comics.advancedSearch(keyword = next, page = page) },
            itemKey = { it.id },
        )
    }

    /** 热搜词只问一次，没拿到就不摆那一块，不挡搜索。 */
    suspend fun ensureHotKeywords() {
        if (hotStarted) return
        hotStarted = true
        hotKeywords = runCatching { pica.meta.keywords() }.getOrDefault(emptyList())
    }
}

@Composable
fun rememberSearchState(): SearchState = remember { SearchState() }

/**
 * 搜索页：顶上一条搜索框，按关键词搜本子（走 `POST comics/advanced-search`）。
 *
 * 还没搜的时候摆一排热搜词，点一个直接搜；结果沿用分类列表那一行本子，滚到底接着续页。
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
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val pager = state.pager

    LaunchedEffect(state) { state.ensureHotKeywords() }
    // 等整页淡进来再弹键盘，不然跟变形动画挤在一起。
    // 从本子详情退回搜索页时不弹：那时候要接着看刚才的结果，不该再顶一层键盘上来。
    LaunchedEffect(Unit) {
        if (!state.consumeFocusRequest()) return@LaunchedEffect
        delay(240)
        runCatching { focus.requestFocus() }
    }

    // 换关键词就回顶部、拉第一页；同一个 pager 不重拉。
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

    val submit: (String?) -> Unit = { text ->
        state.submit(text ?: state.query)
        keyboard?.hide()
    }

    Column(modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                PicaGlyph(
                    icon = PicaIcon.Back,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(Modifier.width(4.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = state::onQueryChange,
                modifier = Modifier.weight(1f).focusRequester(focus),
                placeholder = { Text("搜本子：标题、作者", style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                shape = CircleShape,
                textStyle = MaterialTheme.typography.bodyMedium,
                leadingIcon = {
                    PicaGlyph(
                        icon = PicaIcon.Search,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { state.onQueryChange("") }) {
                            PicaGlyph(
                                icon = PicaIcon.Close,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit(null) }),
                colors = searchFieldColors(),
            )
            TextButton(onClick = { submit(null) }) {
                Text("搜索", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (pager == null) {
            HotKeywordPanel(state = state, onSubmit = submit)
        } else {
            SearchResults(
                pager = pager,
                listState = listState,
                onOpenComic = onOpenComic,
                comicRowModifier = comicRowModifier,
                onRetry = { scope.launch { pager.retry() } },
            )
        }
    }
}

/** 还没搜时的那一块：热搜词 + 一句提示。 */
@Composable
private fun HotKeywordPanel(
    state: SearchState,
    onSubmit: (String?) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(18.dp))
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
            Spacer(Modifier.height(24.dp))
        }
        Text(
            text = "输入关键词，按键盘上的搜索键就能找本子；标题和作者都能搜。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
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

/** 搜索结果：和分类列表一样的单列本子，滚到底续页。 */
@Composable
private fun SearchResults(
    pager: Pager<ComicSummary>,
    listState: LazyListState,
    onOpenComic: (ComicSummary, String) -> Unit,
    comicRowModifier: @Composable (String) -> Modifier,
    onRetry: () -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    ) {
        if (pager.items.isEmpty()) {
            item(key = "search-state") {
                when {
                    pager.error != null -> FailedBlock(pager.error.orEmpty(), onRetry)
                    pager.loading -> LoadingBlock("正在搜…")
                    else -> EmptyBlock("没搜到，换个词试试")
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
                    onRetry = onRetry,
                )
            }
        }
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
