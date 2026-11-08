package com.picacompose.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.picacompose.data.pica
import com.picacompose.picaapi.model.Comment
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.PicaImage
import com.picacompose.ui.components.url
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 「嗶咔留言版」挂的那条占位漫画。
 *
 * 官方 App 没给留言板单独做接口，而是把它当成一本漫画，直接走评论区
 * （`GET/POST comics/{id}/comments`），分页、点赞、置顶、举报全都白拿。
 */
const val FORUM_COMIC_ID = "5822a6e3ad7ede654696e482"

/**
 * 留言板页：和漫画评论区同一套接口，列表 + 底部发留言。
 *
 * pager 和 listState 由 [HomeShell] 持有，退出再进来不重拉第一页。
 */
@Composable
fun ForumContent(
    pager: Pager<Comment>,
    listState: LazyListState = rememberLazyListState(),
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pager) {
        if (pager.items.isEmpty()) pager.loadNext()
    }

    // 滚到离底部还剩几行的时候续页。
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

    Column(modifier.fillMaxSize()) {
        PageScaffold(state = listState, modifier = Modifier.weight(1f)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        PicaGlyph(
                            icon = PicaIcon.Back,
                            modifier = Modifier.size(22.dp),
                            tint = colors.onBackground,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    PageHeader(title = "嗶咔留言版", subtitle = "全站的留言都在这儿，跟评论区同一套。")
                }
                Spacer(Modifier.height(16.dp))
            }
            if (pager.items.isEmpty()) {
                item {
                    when {
                        pager.error != null -> FailedBlock(pager.error.orEmpty()) {
                            scope.launch { pager.retry() }
                        }

                        pager.loading -> LoadingBlock("正在拉留言…")
                        else -> EmptyBlock("还没有人留言")
                    }
                }
            }
            items(pager.items, key = { it.id }) { comment ->
                ForumRow(comment)
            }
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

        Surface(color = colors.surface, tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            error = null
                        },
                        placeholder = { Text("留一句…") },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        shape = RoundedCornerShape(20.dp),
                        maxLines = 4,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        enabled = draft.isNotBlank() && !sending,
                        shape = RoundedCornerShape(20.dp),
                        onClick = {
                            val content = draft.trim()
                            if (content.isEmpty()) return@Button
                            scope.launch {
                                sending = true
                                error = null
                                runCatching { pica.comments.post(FORUM_COMIC_ID, content) }
                                    .onSuccess {
                                        draft = ""
                                        pager.refresh()
                                    }
                                    .onFailure { error = it.message ?: "留言没发出去" }
                                sending = false
                            }
                        },
                    ) {
                        Text(if (sending) "发送中" else "发送")
                    }
                }
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                    )
                }
            }
        }
    }
}

/** 一条留言：头像 + 昵称 + 时间 + 正文 + 赞/回复数。 */
@Composable
private fun ForumRow(comment: Comment) {
    val colors = MaterialTheme.colorScheme
    val avatar = comment.user?.avatar.url()
    PicaCard(modifier = Modifier.padding(bottom = 10.dp)) {
        Row {
            if (avatar != null) {
                PicaImage(
                    url = avatar,
                    contentDescription = comment.user?.name,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(19.dp)),
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(19.dp),
                    color = colors.surfaceVariant,
                    modifier = Modifier.size(38.dp),
                ) {}
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = comment.user?.name.orEmpty().ifBlank { "绅士" },
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (comment.isTop) {
                        Spacer(Modifier.width(8.dp))
                        InfoChip("置顶")
                    }
                    Spacer(Modifier.weight(1f))
                    val time = shortTime(comment.createdAt)
                    if (time.isNotBlank()) {
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                if (comment.content.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = comment.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 12,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val liked = comment.likesCount
                val replies = comment.childsCount
                if (liked > 0 || replies > 0) {
                    Spacer(Modifier.height(8.dp))
                    Row {
                        if (liked > 0) {
                            Text(
                                text = "赞 $liked",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        if (liked > 0 && replies > 0) Spacer(Modifier.width(12.dp))
                        if (replies > 0) {
                            Text(
                                text = "回复 $replies",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
