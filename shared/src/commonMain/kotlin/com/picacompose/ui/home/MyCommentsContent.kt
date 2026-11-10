package com.picacompose.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
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
import com.picacompose.picaapi.model.MyComment
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 历史评论：`GET users/my-comments`，我发过的评论，按时间从新到旧。
 *
 * 点一条就打开它所在的本子（游戏评论没有本子，只能看）。
 */
@Composable
fun MyCommentsContent(
    pager: Pager<MyComment>,
    listState: LazyListState = rememberLazyListState(),
    onBack: () -> Unit,
    onOpenComic: (comicId: String, title: String, key: String) -> Unit = { _, _, _ -> },
    commentRowModifier: @Composable (String) -> Modifier = { Modifier },
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
                if (total > 0 && lastVisible >= total - 4 && pager.hasMore && pager.error == null) {
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
                    title = "历史评论",
                    action = if (pager.total > 0) {
                        { Text("共 ${pager.total} 条", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
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

                    pager.loading -> LoadingBlock("正在拉评论…")
                    else -> EmptyBlock("还没发过评论")
                }
            }
        }
        items(pager.items, key = { it.id }) { comment ->
            val key = "myc-${comment.id}"
            MyCommentRow(
                comment = comment,
                modifier = commentRowModifier(key),
                onClick = {
                    val comic = comment.comic
                    if (comic != null && comic.id.isNotBlank()) {
                        onOpenComic(comic.id, comic.title.orEmpty(), key)
                    }
                },
            )
        }
        if (pager.loading && pager.items.isNotEmpty()) {
            item { LoadingBlock("再拉一页…") }
        }
    }
}

@Composable
private fun MyCommentRow(
    comment: MyComment,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val target = comment.comic?.title?.takeIf { it.isNotBlank() }
        ?: comment.game?.title?.takeIf { it.isNotBlank() }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.surface,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = comment.comic?.id?.isNotBlank() == true, onClick = onClick)
                .padding(14.dp),
        ) {
            if (target != null) {
                Text(
                    text = target,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = relativeTime(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                if (comment.likesCount > 0) {
                    Text(
                        text = "${comment.likesCount} 赞",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                if (comment.childsCount > 0) {
                    Text(
                        text = "${comment.childsCount} 条回复",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                if (comment.hide) {
                    Text(
                        text = "已隐藏",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.error,
                    )
                }
            }
        }
    }
}
