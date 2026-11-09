package com.picacompose.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.picacompose.data.pica
import com.picacompose.picaapi.model.ComicSummary
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 推到底之后还要再推多少才换一批。 */
private val BOTTOM_PULL_THRESHOLD = 88.dp

/**
 * 「隨機本子」分区。
 *
 * 官方这里是 4 个哨兵分区之一：磁贴写死 index 7，扇进 `ComicListFragment("CATEGORY_RANDOM")`，
 * 由 fragment 的 `cR()` 分支走 `GET comics/random`。所以这块和普通分类列表不一样——
 * 接口一次给一批，没有分页、没有排序菜单、也没有翻页栏，想换一批只能重新请求。
 * 这里照这个行为做：一屏列表 + 右上角「換一批」。
 *
 * 到底部再往上推一把也能换一批：推的过程里底下浮出一个小圈随手指走，
 * 够远了就松手——列表自己滑回顶部，再抽新的。
 */
@Composable
fun RandomComicsContent(
    listState: LazyListState = rememberLazyListState(),
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenComic: (ComicSummary, String) -> Unit = { _, _ -> },
    comicRowModifier: @Composable (String) -> Modifier = { Modifier },
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var comics by remember { mutableStateOf<List<ComicSummary>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    // 换一批的整段过程：滑回顶部 + 等新一批回来。
    var refreshing by remember { mutableStateOf(false) }
    // 底部推上来的距离，只用来画那个小圈。
    var pullPx by remember { mutableFloatStateOf(0f) }
    val thresholdPx = with(LocalDensity.current) { BOTTOM_PULL_THRESHOLD.toPx() }

    // 抽一批新的。直接干活，不靠 tick 转手，免得中间哪一步卡住就一直转圈。
    suspend fun reload() {
        loading = true
        error = null
        runCatching { pica.comics.random() }
            .onSuccess { comics = it }
            .onFailure { error = it.message ?: "随机本子没拉到" }
        loading = false
        // 换完从第一本看起（上滑那段的动画可能被手势打断）。
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            listState.requestScrollToItem(0)
        }
        refreshing = false
    }

    fun requestRefresh() {
        if (refreshing) return
        refreshing = true
        pullPx = 0f
        scope.launch {
            // 先自动滑回顶部——这一段本身就是动画，换完再从顶上往下看。
            // 手指还没松时这段动画会被新的手势掐掉（滚动由互斥锁管着），
            // 所以卡时间兜一下，掐掉了也照样接着换。
            withTimeoutOrNull(900) { listState.animateScrollToItem(0) }
            reload()
        }
    }

    LaunchedEffect(Unit) { reload() }

    // 到底了还继续往上推：记下推了多远，够一个门槛就换一批。
    // 推的时候把这段位移吃掉，免得列表再抖一下。
    val bottomPull = remember(listState) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput || refreshing) return Offset.Zero
                if (available.y < 0f && !listState.canScrollForward) {
                    pullPx = (pullPx - available.y).coerceAtMost(thresholdPx * 1.5f)
                    if (pullPx >= thresholdPx) requestRefresh()
                    return Offset(0f, available.y)
                }
                // 手指往回收就把圈收掉。
                if (available.y > 0f) pullPx = 0f
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!refreshing) pullPx = 0f
                return Velocity.Zero
            }
        }
    }

    Box(modifier.fillMaxSize().nestedScroll(bottomPull)) {
        PageScaffold(
            state = listState,
            onRefresh = { reload() },
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
                        PageHeader(title = "隨機本子")
                    }
                    Text(
                        text = "換一批",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { requestRefresh() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
            },
        ) {
            val list = comics
            if (list.isNullOrEmpty()) {
                item(key = "random-state") {
                    when {
                        error != null -> FailedBlock(error.orEmpty()) { requestRefresh() }
                        loading -> LoadingBlock("正在抽本子…")
                        else -> EmptyBlock("这次没抽到本子")
                    }
                }
            }
            if (list != null) {
                items(count = list.size, key = { list[it].id }) { index ->
                    val comic = list[index]
                    val rowKey = "randomComic-${comic.id}"
                    ComicListRow(
                        comic = comic,
                        onClick = { onOpenComic(comic, rowKey) },
                        modifier = comicRowModifier(rowKey),
                    )
                }
            }
            // 随机就一批，没有下一页；底部留一句说明，也顺便教一下这个手势。
            if (!list.isNullOrEmpty() && !loading && error == null) {
                item(key = "random-footer") {
                    Text(
                        text = "就这些——想换一批，点上面「換一批」，或者滑到底再往上推一把",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 18.dp),
                    )
                }
            }
        }

        // 推到底时底下的提示：小圈跟着推的距离长，松开手就转起来表示在换。
        AnimatedVisibility(
            visible = refreshing || pullPx > 0f,
            enter = fadeIn(tween(120)) + slideInVertically(tween(160)) { it / 2 } +
                scaleIn(tween(160), initialScale = 0.9f),
            exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { it / 2 } +
                scaleOut(tween(160), targetScale = 0.9f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 26.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = colors.secondaryContainer,
                tonalElevation = 2.dp,
                shadowElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colors.onSecondaryContainer,
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = { (pullPx / thresholdPx).coerceIn(0f, 1f) },
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colors.onSecondaryContainer,
                        )
                    }
                    Text(
                        text = if (refreshing) "正在换一批…" else "再推一点换一批",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSecondaryContainer,
                    )
                }
            }
        }
    }
}
