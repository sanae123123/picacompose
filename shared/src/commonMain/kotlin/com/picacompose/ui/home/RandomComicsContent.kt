package com.picacompose.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** 推到底之后还要再推多少才换一批。 */
private val BOTTOM_PULL_THRESHOLD = 88.dp

/** 一次换一批最多等这么久，等不到就给个准话，别让圈一直转。 */
private const val SWAP_TIMEOUT_MS = 8_000L

/**
 * 「隨機本子」分区。
 *
 * 官方这里是 4 个哨兵分区之一：磁贴写死 index 7，扇进 `ComicListFragment("CATEGORY_RANDOM")`，
 * 由 fragment 的 `cR()` 分支走 `GET comics/random`。所以这块和普通分类列表不一样——
 * 接口一次给一批，没有分页、没有排序菜单、也没有翻页栏，想换一批只能重新请求。
 * 这里照这个行为做：一屏列表 + 右上角「換一批」。
 *
 * 到底部再往上推一把也能换一批：推的过程里底下浮出一个小圈随手指走，
 * 够远了就换——新一批整块从下面滑上来。
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
    var comics by remember { mutableStateOf<List<ComicSummary>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    // 正在换一批：底下的提示转圈。
    var swapping by remember { mutableStateOf(false) }
    // 换一批没换成时的提示（会自动收掉）。
    var swapError by remember { mutableStateOf<String?>(null) }
    // 底部推上来的距离，只用来画那个小圈。
    var pullPx by remember { mutableFloatStateOf(0f) }
    // 推够了就把这个数加一；一次手势只算一次。
    var swapRequest by remember { mutableIntStateOf(0) }
    var swapPending by remember { mutableStateOf(false) }
    // 换成功一批就加一，用来放一次入场动画。
    var swapTick by remember { mutableIntStateOf(0) }
    val thresholdPx = with(LocalDensity.current) { BOTTOM_PULL_THRESHOLD.toPx() }
    // 0 = 在位；换到新一批时从 1 滑回 0，整块内容往上归位。
    val entrance = remember { Animatable(0f) }

    suspend fun fetch(): List<ComicSummary>? =
        withTimeoutOrNull(SWAP_TIMEOUT_MS) {
            runCatching { pica.comics.random() }.getOrNull()
        }

    suspend fun loadFirst() {
        loading = true
        error = null
        val fresh = fetch()
        if (fresh == null) error = "随机本子没拉到" else comics = fresh
        loading = false
    }

    LaunchedEffect(Unit) { loadFirst() }

    // 换一批：抽新的一批，抽到了就整块滑上来；抽不到就给个准话。
    LaunchedEffect(swapRequest) {
        if (swapRequest == 0) return@LaunchedEffect
        swapping = true
        swapError = null
        try {
            val fresh = fetch()
            if (fresh == null) {
                swapError = "这批没换成，再推一次"
            } else {
                comics = fresh
                error = null
                swapTick++
            }
        } finally {
            swapping = false
            swapPending = false
        }
    }

    // 新一批到位：先归到顶上，再放一次「从下面滑上来」的动画。
    LaunchedEffect(swapTick) {
        if (swapTick == 0) return@LaunchedEffect
        listState.requestScrollToItem(0)
        entrance.snapTo(1f)
        entrance.animateTo(0f, tween(durationMillis = 320, easing = FastOutSlowInEasing))
    }

    // 提示语过一会儿自己收掉，别一直挂在屏幕上。
    LaunchedEffect(swapError) {
        if (swapError != null) {
            delay(2_500)
            swapError = null
        }
    }

    fun requestSwap() {
        if (swapping || swapPending) return
        swapPending = true
        pullPx = 0f
        swapRequest++
    }

    // 到底了还继续往上推：记下推了多远，够一个门槛就换一批。
    // 推的时候把这段位移吃掉，免得列表再抖一下。
    val bottomPull = remember(listState) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput || swapping || swapPending) {
                    return Offset.Zero
                }
                if (available.y < 0f && !listState.canScrollForward) {
                    pullPx = (pullPx - available.y).coerceAtMost(thresholdPx * 1.5f)
                    if (pullPx >= thresholdPx) requestSwap()
                    return Offset(0f, available.y)
                }
                // 手指往回收就把圈收掉。
                if (available.y > 0f) pullPx = 0f
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!swapping && !swapPending) pullPx = 0f
                return Velocity.Zero
            }
        }
    }

    Box(modifier.fillMaxSize().nestedScroll(bottomPull)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = entrance.value * 64.dp.toPx()
                    alpha = 1f - entrance.value * 0.45f
                },
        ) {
            PageScaffold(
                state = listState,
                onRefresh = { loadFirst() },
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
                                .clickable { requestSwap() }
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
                            error != null -> FailedBlock(error.orEmpty()) { requestSwap() }
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
        }

        // 推到底时底下的提示：小圈跟着推的距离长，够远了就转起来表示在换。
        val hintVisible = swapping || swapError != null || pullPx > 0f
        AnimatedVisibility(
            visible = hintVisible,
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
                color = if (swapError != null) colors.errorContainer else colors.secondaryContainer,
                tonalElevation = 2.dp,
                shadowElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (swapping) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colors.onSecondaryContainer,
                        )
                    } else if (swapError == null) {
                        CircularProgressIndicator(
                            progress = { (pullPx / thresholdPx).coerceIn(0f, 1f) },
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colors.onSecondaryContainer,
                        )
                    }
                    Text(
                        text = when {
                            swapping -> "正在换一批…"
                            swapError != null -> swapError.orEmpty()
                            else -> "再推一点换一批"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (swapError != null) {
                            colors.onErrorContainer
                        } else {
                            colors.onSecondaryContainer
                        },
                    )
                }
            }
        }
    }
}
