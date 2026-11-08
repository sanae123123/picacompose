package com.picacompose.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.picacompose.picaapi.model.GameSummary
import com.picacompose.ui.components.PicaImage
import com.picacompose.ui.components.url
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 游戏页：双列瀑布流，一次拉一页，滚到底再续，离屏的格子不会参与组合。
 *
 * pager 和 listState 由 [HomeShell] 持有，切走再切回来不会重新拉第一页。
 */
@Composable
fun GamesContent(
    pager: Pager<GameSummary>,
    listState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
) {
    val scope = rememberCoroutineScope()

    LaunchedEffect(pager) {
        if (pager.items.isEmpty()) pager.loadNext()
    }

    // 滚到离底部还剩四五格的时候续页；pager.loading 挡住重复请求。
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

    StaggeredPageScaffold(
        state = listState,
        header = { PageHeader(title = "游戏", subtitle = "PicACG 站内的小游戏。") },
    ) {
        if (pager.items.isEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) {
                when {
                    pager.error != null -> FailedBlock(pager.error.orEmpty()) {
                        scope.launch { pager.retry() }
                    }

                    pager.loading -> LoadingBlock()
                    else -> EmptyBlock("接口没返回游戏")
                }
            }
        }
        items(count = pager.items.size, key = { pager.items[it].id }) { index ->
            GameCard(pager.items[index])
        }
        if (pager.items.isNotEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) {
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

/** 瀑布流里的一张游戏卡：封面 + 标题 + 小字 publisher。 */
@Composable
private fun GameCard(game: GameSummary) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            PicaImage(
                url = game.icon.url(),
                contentDescription = game.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(coverAspect(game.id)),
            )
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val publisher = game.publisher?.takeIf { it.isNotBlank() }
                if (publisher != null) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = publisher,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 封面宽高比。游戏图标大多方图、少数竖版，用 id 定住几种比例，
 * 两列才会错落成瀑布流；同一个格子滚动复用也还是同一个高度。
 */
private fun coverAspect(id: String): Float {
    val ratios = floatArrayOf(1f, 0.85f, 0.75f, 0.92f, 0.7f, 0.8f)
    return ratios[(id.hashCode() and 0x7fffffff) % ratios.size]
}
