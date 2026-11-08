package com.picacompose.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import com.picacompose.picaapi.model.UserBasic
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.PicaImage
import com.picacompose.ui.components.UserProfileDialog
import com.picacompose.ui.components.UserTitleBadge
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
    Pager(
        load = { page -> pica.comments.list(comicId, page).comments },
        itemKey = { it.id },
    )
}

/**
 * 进评论区时拉数据：第一次进来拉第一页，之后再进来重拉一遍并替换内容。
 *
 * 点赞状态、回复数这些会变，以服务端为准，本地不缓存「我点没点过」。
 */
@Composable
fun CommentAutoRefresh(pager: Pager<Comment>) {
    LaunchedEffect(pager) {
        if (pager.items.isEmpty()) pager.loadNext() else pager.reloadFirstPage()
    }
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
    items(pager.items.distinctBy { it.id }, key = { it.id }) { comment ->
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

/** 点赞按钮：拇指 + 数量，点一下切赞/取消，自己吞掉点击不惊动外层。 */
@Composable
private fun LikeButton(comment: Comment, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    // 键里带上服务端那份数据：重新拉到评论后，状态跟着最新的 isLiked 走。
    var liked by remember(comment.id, comment.isLiked) { mutableStateOf(comment.isLiked) }
    var likes by remember(comment.id, comment.likesCount) { mutableStateOf(comment.likesCount) }
    var busy by remember(comment.id) { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !busy) {
                val target = !liked
                liked = target
                likes = (likes + if (target) 1 else -1).coerceAtLeast(0)
                busy = true
                scope.launch {
                    runCatching { pica.comments.like(comment.id) }
                        .onSuccess { action ->
                            if (action == "like" || action == "unlike") {
                                liked = action == "like"
                                likes = comment.likesCount + if (liked) 1 else 0
                            }
                        }
                        .onFailure {
                            liked = !target
                            likes = comment.likesCount
                        }
                    busy = false
                }
            }
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        PicaGlyph(
            icon = PicaIcon.Like,
            modifier = Modifier.size(15.dp),
            tint = if (liked) colors.primary else colors.onSurfaceVariant,
            filled = liked,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (likes > 0) likes.toString() else "赞",
            style = MaterialTheme.typography.labelSmall,
            color = if (liked) colors.primary else colors.onSurfaceVariant,
        )
    }
}

/**
 * 一条评论：头像 + 昵称 + 时间 + 正文 + 点赞/回复。
 *
 * 有楼中楼时，点正文那一块就地展开，收起再点一次；
 * 楼中楼按需拉 `comments/{id}/childrens`，不预加载。
 */
@Composable
fun CommentRow(
    comment: Comment,
    onReply: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val avatar = comment.user?.avatar.url()
    var expanded by remember(comment.id) { mutableStateOf(false) }
    var introUser by remember(comment.id) { mutableStateOf<UserBasic?>(null) }
    val replyPager = remember(comment.id) {
        Pager(
            load = { page -> pica.comments.children(comment.id, page) },
            itemKey = { it.id },
        )
    }
    val hasReplies = comment.childsCount > 0

    LaunchedEffect(expanded, replyPager) {
        if (expanded && replyPager.items.isEmpty() && replyPager.error == null) replyPager.loadNext()
    }

    PicaCard(modifier = Modifier.padding(bottom = 10.dp)) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (hasReplies) Modifier.clickable { expanded = !expanded } else Modifier),
            ) {
                val user = comment.user
                if (avatar != null) {
                    PicaImage(
                        url = avatar,
                        contentDescription = user?.name,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .then(
                                if (user != null) Modifier.clickable { introUser = user } else Modifier,
                            ),
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(19.dp),
                        color = colors.surfaceVariant,
                        modifier = Modifier
                            .size(38.dp)
                            .then(
                                if (user != null) Modifier.clickable { introUser = user } else Modifier,
                            ),
                    ) {}
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Top) {
                        // 名字放不下就折行，称号跟着掉到下一行，不截成省略号。
                        FlowRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = comment.user?.name.orEmpty().ifBlank { "绅士" },
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.onSurface,
                            )
                            UserTitleBadge(comment.user?.title)
                            if (comment.isTop) InfoChip("置顶")
                        }
                        val time = relativeTime(comment.createdAt)
                        if (time.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
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
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LikeButton(comment)
                        if (hasReplies) {
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = if (expanded) "收起回复" else "${comment.childsCount} 条回复",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (expanded) colors.primary else colors.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { expanded = !expanded }
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
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
            introUser?.let { user ->
                UserProfileDialog(
                    userId = user.id,
                    seedName = user.name,
                    seedAvatarUrl = user.avatar.url(),
                    seedTitle = user.title,
                    seedSlogan = user.slogan,
                    onDismiss = { introUser = null },
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(220)) + fadeIn(tween(160)),
                exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(tween(120)),
            ) {
                CommentReplies(
                    pager = replyPager,
                    onReply = onReply,
                    modifier = Modifier.padding(start = 50.dp, top = 12.dp),
                )
            }
        }
    }
}

/** 楼中楼列表：加载中 / 拉失败重试 / 一条条排列 / 还有下一页时给个「更多回复」。 */
@Composable
private fun CommentReplies(
    pager: Pager<Comment>,
    onReply: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val error = pager.error

    Column(modifier.fillMaxWidth()) {
        if (pager.items.isEmpty()) {
            when {
                error != null -> Text(
                    text = "回复没拉到，点一下重试",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.error,
                    modifier = Modifier
                        .clickable { scope.launch { pager.retry() } }
                        .padding(vertical = 4.dp),
                )
                pager.loading -> Text(
                    text = "正在展开…",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                else -> Unit
            }
        } else {
            pager.items.forEach { child ->
                ReplyRow(comment = child, onReply = onReply)
            }
            if (pager.hasMore) {
                Text(
                    text = if (pager.loading) "正在拉…" else "更多回复",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                    modifier = Modifier
                        .clickable { if (!pager.loading) scope.launch { pager.loadNext() } }
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}

/** 楼中楼里的一条：小头像 + 昵称 + 正文 + 点赞。 */
@Composable
private fun ReplyRow(comment: Comment, onReply: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val avatar = comment.user?.avatar.url()
    val user = comment.user
    var introUser by remember(comment.id) { mutableStateOf<UserBasic?>(null) }

    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        if (avatar != null) {
            PicaImage(
                url = avatar,
                contentDescription = comment.user?.name,
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .then(if (user != null) Modifier.clickable { introUser = user } else Modifier),
            )
        } else {
            Surface(
                shape = RoundedCornerShape(13.dp),
                color = colors.surfaceVariant,
                modifier = Modifier
                    .size(26.dp)
                    .then(if (user != null) Modifier.clickable { introUser = user } else Modifier),
            ) {}
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = comment.user?.name.orEmpty().ifBlank { "绅士" },
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurface,
                    )
                    UserTitleBadge(comment.user?.title)
                }
                val time = relativeTime(comment.createdAt)
                if (time.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            if (comment.content.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = comment.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LikeButton(comment, modifier = Modifier.padding(start = 0.dp))
                if (onReply != null) {
                    Spacer(Modifier.width(10.dp))
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

    introUser?.let { user ->
        UserProfileDialog(
            userId = user.id,
            seedName = user.name,
            seedAvatarUrl = user.avatar.url(),
            seedTitle = user.title,
            seedSlogan = user.slogan,
            onDismiss = { introUser = null },
        )
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
