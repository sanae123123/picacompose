package com.picacompose.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.AlertDialog
import top.yukonga.miuix.kmp.basic.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.picacompose.picaapi.model.ComicSummary
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.rememberDialogPop

/**
 * 页面顶部的标题。只放标题，说明文字不占这儿的位置。
 *
 * [action] 是标题右端那颗按钮（比如分类页的搜索），不传就还是纯标题。
 */
@Composable
fun PageHeader(
    title: String,
    action: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.title2,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (action != null) action()
    }
}

/** 大圆角卡片，页面里的分区都用它装。 */
@Composable
fun PicaCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MiuixTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
fun InfoChip(text: String) {
    val colors = MiuixTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = colors.secondaryContainer.copy(alpha = 0.85f),
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            color = colors.onSecondaryContainer,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** 卡片里的一行：图标 + 标题 + 右侧说明。 */
@Composable
fun DetailRow(
    icon: PicaIcon,
    title: String,
    value: String? = null,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PicaGlyph(icon, Modifier.size(18.dp), colors.onSurfaceSecondary)
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MiuixTheme.textStyles.body1,
            color = colors.onSurface,
        )
        Spacer(Modifier.weight(1f))
        if (!value.isNullOrBlank()) {
            Text(
                text = value,
                style = MiuixTheme.textStyles.body1,
                color = colors.onSurfaceSecondary,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
fun LoadingBlock(text: String = "加载中…") {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = colors.primary),
modifier = Modifier.size(24.dp),
            strokeWidth = 2.5.dp,
        )
        Spacer(Modifier.height(12.dp))
        Text(text, style = MiuixTheme.textStyles.footnote1, color = colors.onSurfaceSecondary)
    }
}

@Composable
fun FailedBlock(message: String, onRetry: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MiuixTheme.textStyles.body1,
            color = colors.onSurfaceSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Button(onClick = onRetry, cornerRadius = 999.dp) { Text("再试一次") }
    }
}

/**
 * 顶栏的收起状态：手指往上推（内容往后翻）收起来，往下拉再露出来，回到顶部一定露出来。
 *
 * 用嵌套滚动拿手指位移，不看列表滚到哪一屏，所以顶栏自身高度变化不会反过来影响判定。
 * 收起距离按顶栏实测高度来定：只有滚过自己的高度才收，这样收起后列表还剩得下滚动余量，
 * 不会因为可视区变大把滚动位置挤回去。内容本来就不够滚的页面，顶栏就一直摆着。
 */
@Stable
private class HeaderCollapseState(private val scrollState: ScrollableState) : State<Boolean> {

    /** 收起顶栏需要先滚过这么多像素，等顶栏量出高度后由容器填进来。 */
    var hideAfterPx: Float = Float.MAX_VALUE

    private var shown by mutableStateOf(true)
    private var accumulatedDown = 0f

    override val value: Boolean get() = shown

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, @Suppress("UNUSED_PARAMETER") source: NestedScrollSource): Offset {
            val dy = available.y
            when {
                // 手指往上推 = 列表往后翻，过一段距离再收起。
                dy < 0f -> {
                    if (!scrollState.canScrollBackward) {
                        accumulatedDown = 0f
                        shown = true
                    } else if (scrollState.canScrollForward) {
                        accumulatedDown += -dy
                        if (shown && accumulatedDown >= hideAfterPx) shown = false
                    }
                }
                // 手指往下拉 = 往回翻，立刻露出来。
                dy > 0f -> {
                    accumulatedDown = 0f
                    if (!shown && dy > 2f) shown = true
                }
            }
            return Offset.Zero
        }
    }
}

@Composable
private fun rememberHeaderCollapse(state: ScrollableState): HeaderCollapseState =
    remember(state) { HeaderCollapseState(state) }

/**
 * 固定的页面顶栏：挂在列表外面，所以不会跟着内容滚走；
 * 往下滚收起、往上滚展开（见 [HeaderCollapseState]）。
 */
@Composable
private fun PageHeaderSlot(
    header: (@Composable () -> Unit)?,
    visible: Boolean,
    onHeightChanged: (Int) -> Unit,
) {
    if (header == null) return
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(animationSpec = tween(durationMillis = 220)) + fadeIn(tween(160)),
        exit = shrinkVertically(animationSpec = tween(durationMillis = 220)) + fadeOut(tween(120)),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(top = 26.dp, bottom = 2.dp)
                    .onSizeChanged { size -> onHeightChanged(size.height) },
            ) {
                header()
            }
        }
    }
}

/** 有固定顶栏时，列表自己的上边距收一点，别让标题和第一张卡之间空出两块间距。 */
@Composable
private fun listContentPadding(base: PaddingValues, hasHeader: Boolean): PaddingValues {
    if (!hasHeader) return base
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = base.calculateStartPadding(layoutDirection),
        top = 10.dp,
        end = base.calculateEndPadding(layoutDirection),
        bottom = base.calculateBottomPadding(),
    )
}

/**
 * 给懒列表套一层下拉刷新：往下拽到头再拉，就调 [onRefresh] 把内容重新拉一遍。
 *
 * [onRefresh] 是挂起函数，转圈一直转到它真的干完；传 null 就什么都不套。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullRefresh(
    onRefresh: (suspend () -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (onRefresh == null) {
        Box(modifier) { content() }
        return
    }
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            if (!refreshing) {
                scope.launch {
                    refreshing = true
                    runCatching { onRefresh() }
                    refreshing = false
                }
            }
        },
        modifier = modifier,
    ) {
        content()
    }
}

/**
 * 整页容器：顶部一根固定标题栏 + 下面 LazyColumn，滚出屏幕的条目不再参与组合。
 *
 * 各页把内容拆成 `item { }` / `items(...)` 放进去；标题用 [header] 传，别塞进列表当第一项。
 */
@Composable
fun PageScaffold(
    state: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 26.dp),
    header: (@Composable () -> Unit)? = null,
    onRefresh: (suspend () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val headerState = rememberHeaderCollapse(state)
    val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
    var headerHeightPx by remember(headerState) { mutableStateOf(0) }
    headerState.hideAfterPx =
        if (header == null) Float.MAX_VALUE else (headerHeightPx + margin).toFloat()
    Column(modifier.fillMaxSize()) {
        PageHeaderSlot(
            header = header,
            visible = headerState.value,
            onHeightChanged = { height -> if (height > headerHeightPx) headerHeightPx = height },
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .nestedScroll(headerState.connection),
            contentAlignment = Alignment.TopCenter,
        ) {
            PullRefresh(
                onRefresh = onRefresh,
                modifier = Modifier.widthIn(max = 460.dp).fillMaxSize(),
            ) {
                LazyColumn(
                    state = state,
                    contentPadding = listContentPadding(contentPadding, header != null),
                    modifier = Modifier.fillMaxSize(),
                    content = content,
                )
            }
        }
    }
}

/**
 * 双列瀑布流容器：和 [PageScaffold] 一样是懒加载，只是换成两列、高度不等的格子。
 *
 * 整宽的内容（错误提示、底部状态）用 `item(span = StaggeredGridItemSpan.FullLine)`。
 */
@Composable
fun StaggeredPageScaffold(
    state: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 26.dp),
    header: (@Composable () -> Unit)? = null,
    onRefresh: (suspend () -> Unit)? = null,
    content: LazyStaggeredGridScope.() -> Unit,
) {
    val headerState = rememberHeaderCollapse(state)
    val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
    var headerHeightPx by remember(headerState) { mutableStateOf(0) }
    headerState.hideAfterPx =
        if (header == null) Float.MAX_VALUE else (headerHeightPx + margin).toFloat()
    Column(modifier.fillMaxSize()) {
        PageHeaderSlot(
            header = header,
            visible = headerState.value,
            onHeightChanged = { height -> if (height > headerHeightPx) headerHeightPx = height },
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .nestedScroll(headerState.connection),
            contentAlignment = Alignment.TopCenter,
        ) {
            PullRefresh(
                onRefresh = onRefresh,
                modifier = Modifier.widthIn(max = 460.dp).fillMaxSize(),
            ) {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    state = state,
                    contentPadding = listContentPadding(contentPadding, header != null),
                    verticalItemSpacing = 12.dp,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                    content = content,
                )
            }
        }
    }
}

/** 列表底部：续页中转圈、续页失败给重试、到底了说一声。 */
@Composable
fun ListFooter(
    loading: Boolean,
    error: String?,
    hasMore: Boolean,
    onRetry: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    when {
        loading -> Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "再拉一页…",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
        }

        error != null -> Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = error,
                style = MiuixTheme.textStyles.footnote1,
                color = colors.error,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onRetry, cornerRadius = 999.dp) { Text("再试一次") }
        }

        !hasMore -> Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "到底了",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
        }

        else -> Spacer(Modifier.height(18.dp))
    }
}

/** 空状态提示。 */
@Composable
fun EmptyBlock(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 36.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
