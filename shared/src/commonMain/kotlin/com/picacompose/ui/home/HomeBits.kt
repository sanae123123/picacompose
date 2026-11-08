package com.picacompose.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon

/** 页面顶部的标题 + 一句说明。 */
@Composable
fun PageHeader(title: String, subtitle: String? = null) {
    val colors = MaterialTheme.colorScheme
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onBackground,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** 大圆角卡片，页面里的分区都用它装。 */
@Composable
fun PicaCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
fun InfoChip(text: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = colors.secondaryContainer.copy(alpha = 0.85f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
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
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PicaGlyph(icon, Modifier.size(18.dp), colors.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
        )
        Spacer(Modifier.weight(1f))
        if (!value.isNullOrBlank()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
fun LoadingBlock(text: String = "加载中…") {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.5.dp,
            color = colors.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

@Composable
fun FailedBlock(message: String, onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Button(onClick = onRetry, shape = CircleShape) { Text("再试一次") }
    }
}

/**
 * 顶栏的显示状态：往下滚收起来，往上滚或者回到顶部再露出来。
 *
 * 就是常见 App 的那套逻辑：滚动方向决定顶栏去留，回到顶部一定露出来。
 * 横向翻页、二级页自己那套滚动不管，只看当前页的主列表。
 */
@Composable
fun rememberHeaderVisible(state: LazyListState): State<Boolean> =
    rememberHeaderVisible(
        key = state,
        position = { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset },
    )

/** 瀑布流版的 [rememberHeaderVisible]。 */
@Composable
fun rememberHeaderVisible(state: LazyStaggeredGridState): State<Boolean> =
    rememberHeaderVisible(
        key = state,
        position = { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset },
    )

@Composable
private fun rememberHeaderVisible(
    key: Any,
    position: () -> Pair<Int, Int>,
): State<Boolean> {
    val visible = remember(key) { mutableStateOf(true) }
    val threshold = with(LocalDensity.current) { 10.dp.toPx() }
    LaunchedEffect(key) {
        var lastIndex = position().first
        var lastOffset = position().second
        var downAccum = 0f
        snapshotFlow(position).collect { (index, offset) ->
            // 过了条目边界就算转方向；同一屏里就比偏移量。
            val delta = when {
                index > lastIndex -> 1f
                index < lastIndex -> -1f
                else -> (offset - lastOffset).toFloat()
            }
            lastIndex = index
            lastOffset = offset
            val atTop = index == 0 && offset <= 2
            when {
                atTop -> {
                    downAccum = 0f
                    visible.value = true
                }
                delta > 0f -> {
                    downAccum += delta
                    if (downAccum >= threshold) visible.value = false
                }
                delta < 0f -> {
                    downAccum = 0f
                    visible.value = true
                }
            }
        }
    }
    return visible
}

/**
 * 固定的页面顶栏：挂在列表外面，所以不会跟着内容滚走；
 * 往下滚收起、往上滚展开（见 [rememberHeaderVisible]）。
 */
@Composable
private fun PageHeaderSlot(header: (@Composable () -> Unit)?, visible: Boolean) {
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
                    .padding(top = 26.dp, bottom = 2.dp),
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
    content: LazyListScope.() -> Unit,
) {
    val headerVisible by rememberHeaderVisible(state)
    Column(modifier.fillMaxSize()) {
        PageHeaderSlot(header = header, visible = headerVisible)
        Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = state,
                contentPadding = listContentPadding(contentPadding, header != null),
                modifier = Modifier.widthIn(max = 460.dp).fillMaxSize(),
                content = content,
            )
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
    content: LazyStaggeredGridScope.() -> Unit,
) {
    val headerVisible by rememberHeaderVisible(state)
    Column(modifier.fillMaxSize()) {
        PageHeaderSlot(header = header, visible = headerVisible)
        Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                state = state,
                contentPadding = listContentPadding(contentPadding, header != null),
                verticalItemSpacing = 12.dp,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.widthIn(max = 460.dp).fillMaxSize(),
                content = content,
            )
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
    val colors = MaterialTheme.colorScheme
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
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }

        error != null -> Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onRetry, shape = CircleShape) { Text("再试一次") }
        }

        !hasMore -> Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "到底了",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
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
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
