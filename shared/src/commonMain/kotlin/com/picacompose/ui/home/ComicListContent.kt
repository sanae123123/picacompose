package com.picacompose.ui.home

import com.picacompose.ui.components.rememberDialogPop
import com.picacompose.ui.components.PicaTextButton
import com.picacompose.ui.components.PicaFilterChip
import com.picacompose.ui.components.PicaAlertDialog
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import com.picacompose.ui.components.PicaDropdownMenu
import com.picacompose.ui.components.PicaDropdownMenuItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.IconButton
import com.picacompose.ui.components.PicaInputChip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.picacompose.data.readingProgressStore
import com.picacompose.ui.components.PicaTextField
import com.picacompose.picaapi.model.ComicSummary
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.PicaImage
import com.picacompose.ui.components.url
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 分类的本子列表：竖排单列，滚到底自动续页。
 *
 * pager 和 listState 由 [HomeShell] 持有：换分类时才换 pager，退出再进来不重拉第一页。
 */
@Composable
fun ComicListContent(
    title: String,
    pager: Pager<ComicSummary>,
    listState: LazyListState = rememberLazyListState(),
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    sort: String? = null,
    onSortChange: (String?) -> Unit = {},
    /** 完结筛选，点了筛选弹窗里的「应用」才变。 */
    finished: ComicFinished = ComicFinished.All,
    onFinishedChange: (ComicFinished) -> Unit = {},
    /** 标签白名单：多个标签要同时包含。服务端只认一个标签，其余在本地过一遍。 */
    tags: List<String> = emptyList(),
    onTagsChange: (List<String>) -> Unit = {},
    /** 标签黑名单：带这些标签的本子不要。服务端不支持排除，全在本地筛。 */
    excludedTags: List<String> = emptyList(),
    onExcludedTagsChange: (List<String>) -> Unit = {},
    /** 点一本就打开详情页；第二个参数是这一行的变形 key。 */
    onOpenComic: (ComicSummary, String) -> Unit = { _, _ -> },
    /** 给每一行挂变形源：详情页的 key 和它配对，点开从这一行长成整屏。 */
    comicRowModifier: @Composable (String) -> Modifier = { Modifier },
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var showJump by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }

    // 筛选弹窗里的候选标签：服务端没有标签字典接口，就拿当前列表里出现过的标签按频次凑。
    // 不用 remember 缓存：列表续页后 items 变了，缓存会让候选一直停在第几页的状态。
    val tagSuggestions = pager.items
        .flatMap { it.tags }
        .filter { it.isNotBlank() }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key }
        .take(40)

    LaunchedEffect(pager) {
        // 换排序会换一个 pager，回到顶部重新看。
        listState.scrollToItem(0)
        if (pager.items.isEmpty()) pager.loadNext()
    }

    // 滚到离底部还剩四五条的时候续页；pager.loading 挡住重复请求。
    LaunchedEffect(listState, pager) {
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

    PageScaffold(
        state = listState,
        modifier = modifier,
        onRefresh = { pager.reloadFirstPage() },
        header = {
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
                    PageHeader(title = title)
                }
                // 官方列表右上角有个总数，这里也给一个；加了标签筛之后总数没意义，显示筛出来的条数。
                val countText = if (tags.isEmpty() && excludedTags.isEmpty()) {
                    if (pager.total > 0) "${compactCount(pager.total)} 本" else null
                } else {
                    "已筛 ${pager.items.size} 本"
                }
                if (countText != null) {
                    Text(
                        text = countText,
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceSecondary,
                    )
                }
                // 筛选：排序方式 + 完结状态，点开弹窗配。开了筛选就把图标点亮。
                IconButton(
                    onClick = { showFilter = true },
                    modifier = Modifier.size(40.dp),
                ) {
                    PicaGlyph(
                        icon = PicaIcon.Filter,
                        modifier = Modifier.size(22.dp),
                        tint = if (finished == ComicFinished.All && tags.isEmpty() && excludedTags.isEmpty()) {
                            colors.onBackground
                        } else {
                            colors.primary
                        },
                    )
                }
                // 汉堡菜单：跳页 + 排序方式。
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
                    PicaDropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        PicaDropdownMenuItem(
                            text = { Text("跳页") },
                            leadingIcon = {
                                PicaGlyph(PicaIcon.ArrowRight, Modifier.size(18.dp), colors.onSurfaceSecondary)
                            },
                            onClick = {
                                menuOpen = false
                                showJump = true
                            },
                        )
                        HorizontalDivider()
                        Text(
                            text = "排序方式",
                            style = MiuixTheme.textStyles.footnote2,
                            color = colors.onSurfaceSecondary,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                        )
                        ComicSort.entries.forEach { option ->
                            PicaDropdownMenuItem(
                                text = { Text(option.label) },
                                trailingIcon = {
                                    if (option.value == sort) {
                                        PicaGlyph(PicaIcon.Check, Modifier.size(18.dp), colors.primary)
                                    }
                                },
                                onClick = {
                                    menuOpen = false
                                    onSortChange(option.value)
                                },
                            )
                        }
                    }
                }
            }
        },
    ) {
        if (pager.items.isEmpty()) {
            item {
                when {
                    pager.error != null -> FailedBlock(pager.error.orEmpty()) {
                        scope.launch { pager.retry() }
                    }

                    pager.loading -> LoadingBlock("正在拉本子…")
                    else -> EmptyBlock("这个分类还没有本子")
                }
            }
        }
        items(count = pager.items.size, key = { pager.items[it].id }) { index ->
            val comic = pager.items[index]
            val rowKey = "listComic-${comic.id}"
            ComicListRow(
                comic = comic,
                onClick = { onOpenComic(comic, rowKey) },
                modifier = comicRowModifier(rowKey),
            )
        }
        if (pager.items.isNotEmpty()) {
            item(key = "comic-list-footer") {
                ListFooter(
                    loading = pager.loading,
                    error = pager.error,
                    hasMore = pager.hasMore,
                    onRetry = { scope.launch { pager.retry() } },
                )
            }
        }
    }

    if (showJump) {
        PagerJumpDialog(
            pager = pager,
            onDismiss = { showJump = false },
            onJumped = { scope.launch { listState.scrollToItem(0) } },
        )
    }

    if (showFilter) {
        ComicFilterDialog(
            sort = sort,
            finished = finished,
            tags = tags,
            excludedTags = excludedTags,
            tagSuggestions = tagSuggestions,
            onApply = { newSort, newFinished, newTags, newExcluded ->
                showFilter = false
                onSortChange(newSort)
                onFinishedChange(newFinished)
                onTagsChange(newTags)
                onExcludedTagsChange(newExcluded)
            },
            onDismiss = { showFilter = false },
        )
    }
}

/**
 * 筛选弹窗：排序方式、完结状态和标签。
 *
 * 里面是草稿，点「应用」才真的换条件（每点一下就重拉一遍列表太浪费请求）。
 * 标签可以加好几个，语义是同时包含。
 */
@Composable
internal fun ComicFilterDialog(
    sort: String?,
    finished: ComicFinished,
    tags: List<String>,
    excludedTags: List<String>,
    tagSuggestions: List<String>,
    onApply: (String?, ComicFinished, List<String>, List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val pop = rememberDialogPop(onDismiss)
    var draftSort by remember { mutableStateOf(sort) }
    var draftFinished by remember { mutableStateOf(finished) }
    var draftTags by remember { mutableStateOf(tags) }
    var draftExcluded by remember { mutableStateOf(excludedTags) }
    var tagInput by remember { mutableStateOf("") }
    // 新加的标签算「包含」还是「排除」：同一个输入框，靠这个小开关切换。
    var excludeMode by remember { mutableStateOf(false) }

    fun addTag(raw: String) {
        val tag = raw.trim()
        if (tag.isEmpty()) return
        if (excludeMode) {
            if (draftExcluded.none { it.equals(tag, ignoreCase = true) }) {
                draftExcluded = draftExcluded + tag
            }
            draftTags = draftTags.filterNot { it.equals(tag, ignoreCase = true) }
        } else {
            if (draftTags.none { it.equals(tag, ignoreCase = true) }) draftTags = draftTags + tag
            draftExcluded = draftExcluded.filterNot { it.equals(tag, ignoreCase = true) }
        }
        tagInput = ""
    }

    PicaAlertDialog(
        onDismissRequest = pop::dismiss,
        modifier = pop.modifier,
        title = { Text(text = "筛选", style = MiuixTheme.textStyles.title4) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "排序方式",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ComicSort.entries.forEach { option ->
                        PicaFilterChip(
                            selected = draftSort == option.value,
                            onClick = { draftSort = option.value },
                            label = {
                                Text(text = option.label, style = MiuixTheme.textStyles.footnote1)
                            },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "完结状态",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ComicFinished.entries.forEach { option ->
                        PicaFilterChip(
                            selected = draftFinished == option,
                            onClick = { draftFinished = option },
                            label = {
                                Text(text = option.label, style = MiuixTheme.textStyles.footnote1)
                            },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "标签筛选",
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    if (draftTags.isNotEmpty() || draftExcluded.isNotEmpty()) {
                        PicaTextButton(onClick = {
                            draftTags = emptyList()
                            draftExcluded = emptyList()
                        }) {
                            Text(text = "清空", style = MiuixTheme.textStyles.footnote2)
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "包含的都要有，排除的都不能有。第一个「包含」走接口，其余在本地筛；标签不必是列表里出现过的。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.onSurfaceSecondary,
                )
                Spacer(Modifier.height(8.dp))
                // 先选好这一条要加进哪一边，再输入 / 点标签，位置就在输入框上面，不用往下翻。
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PicaFilterChip(
                        selected = !excludeMode,
                        onClick = { excludeMode = false },
                        label = { Text(text = "包含", style = MiuixTheme.textStyles.footnote1) },
                    )
                    PicaFilterChip(
                        selected = excludeMode,
                        onClick = { excludeMode = true },
                        label = { Text(text = "排除", style = MiuixTheme.textStyles.footnote1) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PicaTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        singleLine = true,
                        textStyle = MiuixTheme.textStyles.footnote1,
                        placeholder = if (excludeMode) "输入要排除的标签" else "输入要包含的标签",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addTag(tagInput) }),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    PicaTextButton(onClick = { addTag(tagInput) }, enabled = tagInput.isNotBlank()) {
                        Text(if (excludeMode) "排除" else "包含")
                    }
                }
                if (draftTags.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "已包含",
                        style = MiuixTheme.textStyles.footnote2,
                        color = colors.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        draftTags.forEach { tag ->
                            PicaInputChip(
                                selected = true,
                                onClick = { draftTags = draftTags - tag },
                                label = {
                                    Text(text = tag, style = MiuixTheme.textStyles.footnote1)
                                },
                                trailingIcon = {
                                    PicaGlyph(
                                        icon = PicaIcon.Close,
                                        modifier = Modifier.size(14.dp),
                                        tint = colors.onSurfaceSecondary,
                                    )
                                },
                            )
                        }
                    }
                }
                if (draftExcluded.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "已排除",
                        style = MiuixTheme.textStyles.footnote2,
                        color = colors.error,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        draftExcluded.forEach { tag ->
                            PicaInputChip(
                                selected = true,
                                onClick = { draftExcluded = draftExcluded - tag },
                                label = {
                                    Text(
                                        text = tag,
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = colors.error,
                                    )
                                },
                                trailingIcon = {
                                    PicaGlyph(
                                        icon = PicaIcon.Close,
                                        modifier = Modifier.size(14.dp),
                                        tint = colors.error,
                                    )
                                },
                            )
                        }
                    }
                }
                // 快速添加：列表里出现过的标签直接点就进当前这一边，输入了字就只留对得上的。
                val typed = tagInput.trim()
                val remaining = tagSuggestions
                    .filter { candidate ->
                        (draftTags + draftExcluded).none { it.equals(candidate, ignoreCase = true) }
                    }
                    .filter { candidate -> typed.isEmpty() || candidate.contains(typed, ignoreCase = true) }
                    .take(24)
                if (remaining.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (typed.isEmpty()) {
                            "点一下加到${if (excludeMode) "排除" else "包含"}："
                        } else {
                            "匹配「$typed」，点一下加到${if (excludeMode) "排除" else "包含"}："
                        },
                        style = MiuixTheme.textStyles.footnote2,
                        color = colors.onSurfaceSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        remaining.forEach { tag ->
                            PicaFilterChip(
                                selected = false,
                                onClick = { addTag(tag) },
                                label = {
                                    Text(text = tag, style = MiuixTheme.textStyles.footnote1)
                                },
                            )
                        }
                    }
                } else if (typed.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "列表里没有对得上的，点上面的「${if (excludeMode) "排除" else "包含"}」直接用「$typed」。",
                        style = MiuixTheme.textStyles.footnote2,
                        color = colors.onSurfaceSecondary,
                    )
                }

            }
        },
        confirmButton = {
            PicaTextButton(onClick = {
                onApply(draftSort, draftFinished, draftTags, draftExcluded)
            }) { Text("应用") }
        },
        dismissButton = {
            PicaTextButton(onClick = pop::dismiss) { Text("取消") }
        },
    )
}

/** 完结筛选：`f` 参数，null 是不筛。 */
enum class ComicFinished(val label: String, val value: Boolean?) {
    All("全部", null),
    Finished("已完结", true),
    Ongoing("连载中", false),
}

/** 排序方式：`s` 参数跟官方一致，默认就是服务端原来的顺序。搜索页也用这一套。 */
internal enum class ComicSort(val label: String, val value: String?) {
    Default("默认", null),
    Newest("最新", "dd"),
    Oldest("最早", "da"),
    MostLiked("最多爱心", "ld"),
    MostViewed("最多浏览", "vd"),
}

/** 单列里的一条：左边封面，右边标题 / 作者 / 数据，够一屏塞五六条。随机页也用它。 */
@Composable
internal fun ComicListRow(
    comic: ComicSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colors.tertiaryContainer,
        modifier = modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val progress = remember(comic.id) { readingProgressStore.load(comic.id) }
            Box(
                modifier = Modifier.width(96.dp).aspectRatio(3f / 4f),
            ) {
                PicaImage(
                    url = comic.thumb.url(),
                    contentDescription = comic.title,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                )
                // 读过就标一下读到第几话，一眼知道这本翻到哪儿了。
                if (progress != null && progress.order > 0) {
                    Text(
                        text = "看到第 ${progress.order} 话",
                        style = MiuixTheme.textStyles.footnote2,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.62f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = comic.title,
                    style = MiuixTheme.textStyles.subtitle,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val author = comic.author?.takeIf { it.isNotBlank() }
                if (author != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = author,
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${compactCount(comic.totalLikes)} 赞",
                        style = MiuixTheme.textStyles.footnote2,
                        color = colors.onSurfaceSecondary,
                    )
                    Text(
                        text = "${compactCount(comic.totalViews)} 浏览",
                        style = MiuixTheme.textStyles.footnote2,
                        color = colors.onSurfaceSecondary,
                    )
                    Text(
                        text = "${comic.pagesCount} 页",
                        style = MiuixTheme.textStyles.footnote2,
                        color = colors.onSurfaceSecondary,
                    )
                }
                val categories = comic.categories.take(3)
                if (categories.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.forEach { category ->
                            Text(
                                text = category,
                                style = MiuixTheme.textStyles.footnote2,
                                color = colors.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (comic.finished) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "已完结",
                        style = MiuixTheme.textStyles.footnote2,
                        color = colors.onSurfaceSecondary,
                    )
                }
            }
        }
    }
}
