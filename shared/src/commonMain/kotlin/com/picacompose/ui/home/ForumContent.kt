package com.picacompose.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.picacompose.data.pica
import com.picacompose.picaapi.model.Comment
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import kotlinx.coroutines.launch

/**
 * 「嗶咔留言版」页：官方就是拿占位漫画的评论区当留言板，这里也一样。
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
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    LaunchedEffect(pager) {
        if (pager.items.isEmpty()) pager.loadNext()
    }
    CommentLoadMore(listState, pager)

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
            commentItems(
                pager = pager,
                onRetry = { scope.launch { pager.retry() } },
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
}
