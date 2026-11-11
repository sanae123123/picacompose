package com.picacompose.ui.home

import com.picacompose.ui.components.PicaIconButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.picacompose.data.pica
import com.picacompose.picaapi.model.Comment
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import kotlinx.coroutines.launch

/**
 * 「哔咔留言板」页：官方就是拿占位漫画的评论区当留言板，这里也一样。
 *
 * 列表、行、输入条都在 [CommentBits] 里，漫画详情页的评论区直接复用同一套。
 * pager 和 listState 由 [HomeShell] 持有，退出再进来不重拉第一页。
 */
@Composable
fun ForumContent(
    pager: Pager<Comment>,
    listState: LazyListState = rememberLazyListState(),
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    topComments: List<Comment> = emptyList(),
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    // 要回复谁：非空就弹回复面板。
    var replyTarget by remember { mutableStateOf<CommentReplyTarget?>(null) }
    var showJump by remember { mutableStateOf(false) }

    CommentAutoRefresh(pager)
    CommentLoadMore(listState, pager)

    Column(modifier.fillMaxSize().imePadding()) {
        PageScaffold(
            state = listState,
            modifier = Modifier.weight(1f),
            onRefresh = { pager.reloadFirstPage() },
            header = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PicaIconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        PicaGlyph(
                            icon = PicaIcon.Back,
                            modifier = Modifier.size(22.dp),
                            tint = colors.onBackground,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.weight(1f)) {
                        PageHeader(title = "哔咔留言板")
                    }
                    // 工具栏右上角：评论翻到几页之后，直接输页码跳。
                    Text(
                        text = "跳页",
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showJump = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
            },
        ) {
            commentItems(
                pager = pager,
                onRetry = { scope.launch { pager.retry() } },
                onReply = { replyTarget = it },
                topComments = topComments,
                loadingText = "正在拉留言…",
                emptyText = "还没有人留言",
            )
        }
        CommentComposer(
            placeholder = "留一句…",
            onSend = { content ->
                pica.comments.post(FORUM_COMIC_ID, content)
                pager.refresh()
            },
        )
    }

    if (showJump) {
        PagerJumpDialog(
            pager = pager,
            onDismiss = { showJump = false },
            // 跳完回到顶部，那一页从第一条开始看。
            onJumped = { scope.launch { listState.scrollToItem(0) } },
        )
    }

    replyTarget?.let { target ->
        CommentReplyDialog(
            target = target,
            onDismiss = { replyTarget = null },
            onReplied = { scope.launch { pager.reloadFirstPage() } },
        )
    }
}
