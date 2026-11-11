package com.picacompose.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import com.picacompose.ui.components.PicaTextButton
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
import com.picacompose.ui.components.UserAvatar
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
    onNewestLoaded: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var viewing by remember { mutableStateOf<NotificationDoc?>(null) }

    LaunchedEffect(pager) {
        if (pager.items.isEmpty()) pager.loadNext()
    }

    // 拉到第一页就把最新那条记成已看，首页的红点跟着灭。
    val newestId = pager.items.firstOrNull()?.id
    LaunchedEffect(newestId) {
        if (!newestId.isNullOrBlank()) onNewestLoaded(newestId)
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
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                }
                Spacer(Modifier.width(6.dp))
                PageHeader(title = "消息")
            }
        },
    ) {
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
    val colors = MiuixTheme.colorScheme
    PicaCard(modifier = Modifier.padding(bottom = 10.dp).clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.Top) {
            NotificationAvatar(message)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = message.title.ifBlank { "站内通知" },
                    style = MiuixTheme.textStyles.subtitle,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (message.content.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = message.content,
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val time = relativeTime(message.createdAt)
                if (time.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = time,
                        style = MiuixTheme.textStyles.footnote2,
                        color = colors.onSurfaceSecondary,
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
                        .clip(RoundedCornerShape(12.dp)),
                )
            }
        }
    }
}

/** 圆形头像；系统通知没有发信人，用铃铛占位。 */
@Composable
private fun NotificationAvatar(message: NotificationDoc) {
    val colors = MiuixTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = colors.secondaryContainer,
        modifier = Modifier.size(42.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (message.system) {
                PicaGlyph(PicaIcon.Notice, Modifier.size(20.dp), colors.onSecondaryContainer)
            } else {
                val avatarUrl = message.sender?.avatar.url()
                if (avatarUrl != null) {
                    UserAvatar(
                        avatarUrl = avatarUrl,
                        frameUrl = message.sender?.character,
                        size = 42.dp,
                    )
                } else {
                    Text(
                        text = message.sender?.name?.take(1) ?: "·",
                        style = MiuixTheme.textStyles.title4,
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
    val colors = MiuixTheme.colorScheme
    val pop = rememberDialogPop(onDismiss)
    Dialog(onDismissRequest = pop::dismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = colors.surfaceContainerHigh,
            modifier = pop.modifier,
        ) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
                Text(
                    text = message.title.ifBlank { "站内通知" },
                    style = MiuixTheme.textStyles.title4,
                    color = colors.onSurface,
                )
                val time = relativeTime(message.createdAt)
                if (time.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = time,
                        style = MiuixTheme.textStyles.footnote2,
                        color = colors.onSurfaceSecondary,
                    )
                }
                if (message.content.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = message.content,
                        style = MiuixTheme.textStyles.body1,
                        color = colors.onSurfaceSecondary,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    PicaTextButton(onClick = pop::dismiss) { Text("关闭") }
                }
            }
        }
    }
}

