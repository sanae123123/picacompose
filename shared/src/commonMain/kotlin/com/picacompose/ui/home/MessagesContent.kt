package com.picacompose.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.picacompose.picaapi.model.NotificationDoc
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.rememberDialogPop
import com.picacompose.ui.components.url
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 消息页：站内回复、@ 和系统通知，滚到底再续页。
 *
 * pager 和 listState 由 [HomeShell] 持有，从首页进来再退出去不会重新拉第一页。
 */
@Composable
fun MessagesContent(
    pager: Pager<NotificationDoc>,
    listState: LazyListState = rememberLazyListState(),
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var viewing by remember { mutableStateOf<NotificationDoc?>(null) }

    LaunchedEffect(pager) {
        if (pager.items.isEmpty()) pager.loadNext()
    }

    // 滚到离底部还剩几行的时候续页；pager.loading 挡住重复请求。
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

    PageScaffold(state = listState, modifier = modifier) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    PicaGlyph(
                        icon = PicaIcon.Back,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(Modifier.width(6.dp))
                PageHeader(title = "消息", subtitle = "回复、@ 和系统通知。")
            }
            Spacer(Modifier.height(16.dp))
        }
        if (pager.items.isEmpty()) {
            item {
                when {
                    pager.error != null -> FailedBlock(pager.error.orEmpty()) {
                        scope.launch { pager.retry() }
                    }

                    pager.loading -> LoadingBlock("正在拉消息…")
                    else -> EmptyBlock("还没有消息")
                }
            }
        }
        items(pager.items, key = { it.id }) { message ->
            MessageRow(message = message, onClick = { viewing = message })
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

    viewing?.let { message ->
        MessageDialog(message = message, onDismiss = { viewing = null })
    }
}

/** 一条通知：发信人头像 + 标题 + 正文，右侧挂被评论/回复的那本书的封面。 */
@Composable
private fun MessageRow(
    message: NotificationDoc,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    PicaCard(modifier = Modifier.padding(bottom = 10.dp).clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.Top) {
            NotificationAvatar(message)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = message.title.ifBlank { "站内通知" },
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (message.content.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val time = shortTime(message.createdAt)
                if (time.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            val coverUrl = message.cover.url()
            if (coverUrl != null) {
                Spacer(Modifier.width(10.dp))
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(46.dp)
                        .height(62.dp)
                        .clip(MaterialTheme.shapes.small),
                )
            }
        }
    }
}

/** 圆形头像；系统通知没有发信人，用铃铛占位。 */
@Composable
private fun NotificationAvatar(message: NotificationDoc) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = colors.secondaryContainer,
        modifier = Modifier.size(42.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (message.system) {
                PicaGlyph(PicaIcon.Notice, Modifier.size(20.dp), colors.onSecondaryContainer)
            } else {
                val avatarUrl = message.sender?.avatar.url()
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Text(
                        text = message.sender?.name?.take(1) ?: "·",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

/** 点开一条消息看全文；正文太长，列表里只能截断。 */
@Composable
private fun MessageDialog(
    message: NotificationDoc,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val pop = rememberDialogPop(onDismiss)
    Dialog(onDismissRequest = pop::dismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = colors.surfaceContainerHigh,
            tonalElevation = 4.dp,
            modifier = pop.modifier,
        ) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
                Text(
                    text = message.title.ifBlank { "站内通知" },
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                val time = shortTime(message.createdAt)
                if (time.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                if (message.content.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = pop::dismiss) { Text("关闭") }
                }
            }
        }
    }
}

/** ISO 时间截成 `MM/dd HH:mm`；只为显示，不引入日期库。 */
private fun shortTime(iso: String?): String {
    if (iso.isNullOrBlank() || iso.length < 16) return ""
    val date = iso.substring(5, 10).replace('-', '/')
    val time = iso.substring(11, 16)
    return "$date $time"
}
