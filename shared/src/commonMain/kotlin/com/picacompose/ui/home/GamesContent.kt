package com.picacompose.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.picacompose.picaapi.model.GameSummary
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 游戏页：一次拉一页，滚到底再续，离屏的条目不会参与组合。
 *
 * pager 和 listState 由 [HomeShell] 持有，切走再切回来不会重新拉第一页。
 */
@Composable
fun GamesContent(
    pager: Pager<GameSummary>,
    listState: LazyListState = rememberLazyListState(),
) {
    val scope = rememberCoroutineScope()

    LaunchedEffect(pager) {
        if (pager.items.isEmpty()) pager.loadNext()
    }

    // 滚到离底部还剩三条的时候续页；pager.loading 挡住重复请求。
    LaunchedEffect(listState, pager) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (total > 0 && lastVisible >= total - 4 && pager.hasMore && pager.error == null) {
                    pager.loadNext()
                }
            }
    }

    PageScaffold(state = listState) {
        item {
            PageHeader(title = "游戏", subtitle = "PicACG 站内的小游戏。")
            Spacer(Modifier.height(18.dp))
        }
        if (pager.items.isEmpty()) {
            item {
                when {
                    pager.error != null -> FailedBlock(pager.error.orEmpty()) {
                        scope.launch { pager.retry() }
                    }

                    pager.loading -> LoadingBlock()
                    else -> EmptyBlock("接口没返回游戏")
                }
            }
        }
        items(pager.items.size) { index -> GameRow(pager.items[index]) }
        if (pager.items.isNotEmpty()) {
            item {
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

@Composable
private fun GameRow(game: GameSummary) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(
                    game.publisher,
                    game.version?.let { "v$it" },
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            if (game.android) InfoChip("安卓")
        }
    }
}
