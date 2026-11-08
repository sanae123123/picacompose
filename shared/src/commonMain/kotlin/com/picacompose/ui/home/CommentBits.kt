package com.picacompose.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import com.picacompose.ui.components.PicaImage
import com.picacompose.ui.components.url
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 官方 App 的「嗶咔留言版」挂在这条占位漫画上（标题「愛嗶咔請留言」）。
 *
 * 留言板没做独立接口，就是评论区换了个漫画 id；所以留言板和以后漫画详情页的
 * 评论区共用本文件这套实现，别各写一份。
 */
const val FORUM_COMIC_ID = "5822a6e3ad7ede654696e482"

/** 按漫画 id 建一个评论分页器；留言板传 [FORUM_COMIC_ID]。 */
@Composable
fun rememberCommentPager(comicId: String): Pager<Comment> = remember(comicId) {
    Pager { page -> pica.comments.list(comicId, page).comments }
}

/** 滚到离底部还剩几行时续页。评论区和留言板共用。 */
@Composable
fun CommentLoadMore(listState: LazyListState, pager: Pager<*>) {
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
}

/**
 * 评论区正文：加载态 / 列表 / 续页脚注整块塞进调用方的懒列表。
 *
 * 留言板和漫画详情页都调它，各自只负责自己的页头。
 */
fun LazyListScope.commentItems(
    pager: Pager<Comment>,
    onRetry: () -> Unit,
    onReply: ((Comment) -> Unit)? = null,
    loadingText: String = "正在拉评论…",
    emptyText: String = "还没有评论",
) {
    if (pager.items.isEmpty()) {
        item {
            when {
                pager.error != null -> FailedBlock(pager.error.orEmpty(), onRetry)
                pager.loading -> LoadingBlock(loadingText)
                else -> EmptyBlock(emptyText)
            }
        }
    }
    items(pager.items, key = { it.id }) { comment ->
        CommentRow(comment = comment, onReply = onReply?.let { reply -> { reply(comment) } })
    }
    if (pager.items.isNotEmpty()) {
        item {
            ListFooter(
                loading = pager.loading,
                error = pager.error,
                hasMore = pager.hasMore,
                onRetry = onRetry,
            )
        }
    }
}

/** 一条评论：头像 + 昵称 + 时间 + 正文 + 赞/回复数。 */
@Composable
fun CommentRow(
    comment: Comment,
    onReply: (() -> Unit)? = null,
) {
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
                if (liked > 0 || replies > 0 || onReply != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                        if (onReply != null) {
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "回复",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.primary,
                                modifier = Modifier
                                    .clickable(onClick = onReply)
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 底部输入条：留言板和漫画评论区共用。
 *
 * [onSend] 里抛异常就显示红字，成功后自己清空输入框；发完刷新列表由调用方在
 * [onSend] 里做（传进来的内容已经 trim 过、非空）。
 */
@Composable
fun CommentComposer(
    onSend: suspend (content: String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "留一句…",
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Surface(color = colors.surface, tonalElevation = 3.dp, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        error = null
                    },
                    placeholder = { Text(placeholder) },
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
                            runCatching { onSend(content) }
                                .onSuccess { draft = "" }
                                .onFailure { error = it.message ?: "没发出去" }
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
