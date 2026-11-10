package com.picacompose.ui.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.picacompose.picaapi.model.ComicSummary
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 我的收藏：`GET users/favourite`，单列，滚到底续页。
 *
 * pager 和 listState 由 [HomeShell] 持有，退出去再进来不重拉第一页（每次打开会刷新一次）。
 */
@Composable
fun FavouritesContent(
    pager: Pager<ComicSummary>,
    listState: LazyListState = rememberLazyListState(),
    onBack: () -> Unit,
    onOpenComic: (ComicSummary, String) -> Unit = { _, _ -> },
    comicRowModifier: @Composable (String) -> Modifier = { Modifier },
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(pager) {
        if (pager.items.isEmpty()) pager.loadNext()
    }

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
                PageHeader(
                    title = "我的收藏",
                    action = if (pager.total > 0) {
                        { Text("共 ${pager.total} 本", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
                    } else {
                        null
                    },
                )
            }
        },
    ) {
        if (pager.items.isEmpty()) {
            item {
                when {
                    pager.error != null -> FailedBlock(pager.error.orEmpty()) {
                        scope.launch { pager.retry() }
                    }

                    pager.loading -> LoadingBlock("正在拉收藏…")
                    else -> EmptyBlock("还没有收藏，去本子详情页点个星星吧")
                }
            }
        }
        items(pager.items, key = { it.id }) { comic ->
            val key = "fav-${comic.id}"
            ComicListRow(
                comic = comic,
                onClick = { onOpenComic(comic, key) },
                modifier = comicRowModifier(key),
            )
        }
        if (pager.loading && pager.items.isNotEmpty()) {
            item { LoadingBlock("再拉一页…") }
        }
    }
}
