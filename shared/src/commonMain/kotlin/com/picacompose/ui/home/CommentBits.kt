package com.picacompose.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import com.picacompose.ui.components.PicaAlertDialog
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import com.picacompose.ui.components.PicaTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.picacompose.data.pica
import com.picacompose.ui.components.PicaTextField
import com.picacompose.picaapi.model.Comment
import com.picacompose.picaapi.model.UserBasic
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.PicaImage
import com.picacompose.ui.components.UserAvatar
import com.picacompose.ui.components.UserProfileDialog
import com.picacompose.ui.components.rememberDialogPop
import com.picacompose.ui.components.UserTitleBadge
import com.picacompose.ui.components.url
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 官方 App 的「哔咔留言板」挂在这条占位漫画上（标题「爱哔咔请留言」）。
 *
 * 留言板没做独立接口，就是评论区换了个漫画 id；所以留言板和以后漫画详情页的
 * 评论区共用本文件这套实现，别各写一份。
 */
const val FORUM_COMIC_ID = "5822a6e3ad7ede654696e482"

/**
 * 评论区状态：分页列表 + 置顶评论。
 *
 * 接口把置顶评论单独放在 `topComments` 里，不混在 `comments.docs` 里，所以两套都留着；
 * 置顶的每次拉第一页时更新，翻到第几页它们都还在最上面。
 */
@Stable
class CommentFeed(comicId: String) {
    var topComments by mutableStateOf<List<Comment>>(emptyList())
        private set

    val pager: Pager<Comment> = Pager(
        load = { page ->
            val data = pica.comments.list(comicId, page)
            // 置顶跟着第一页走，翻页不重复拉。
            if (page == 1) topComments = data.topComments
            data.comments
        },
        itemKey = { it.id },
    )
}

/** 按漫画 id 建一份评论区状态；留言板传 [FORUM_COMIC_ID]。 */
@Composable
fun rememberCommentFeed(comicId: String): CommentFeed = remember(comicId) { CommentFeed(comicId) }

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
 * 要回复谁。
 *
 * [root] 是楼中楼挂靠的那条主评论（楼里回复也挂同一层）；[mentionName] 不为空时
 * 输入框先填好「@某人 」，让对方知道在回谁。
 */
data class CommentReplyTarget(
    val root: Comment,
    val mentionName: String? = null,
)

/**
 * 长按一条留言：问一句要不要举报，确认后调 `comments/{id}/report`。
 *
 * 举报是提交给站务的，删不删由他们定，所以文案别承诺结果。
 */
@Composable
private fun ReportCommentDialog(comment: Comment, onDismiss: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val pop = rememberDialogPop(onDismiss)
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    PicaAlertDialog(
        onDismissRequest = pop::dismiss,
        modifier = pop.modifier,
        title = {
            Text(
                text = if (reported) "已提交" else "举报这条留言？",
                style = MiuixTheme.textStyles.title4,
            )
        },
        text = {
            Column {
                if (reported) {
                    Text(
                        text = "已经提交给站务了，处理结果以他们为准。",
                        style = MiuixTheme.textStyles.body1,
                        color = colors.onSurfaceSecondary,
                    )
                } else {
                    Text(
                        text = "${comment.user?.name.orEmpty().ifBlank { "这位绅士" }}：${comment.content}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceSecondary,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "确认要举报的话就点下面的举报，提交给站务处理。",
                        style = MiuixTheme.textStyles.body1,
                        color = colors.onSurface,
                    )
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = it,
                            style = MiuixTheme.textStyles.footnote1,
                            color = colors.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (reported) {
                PicaTextButton(onClick = pop::dismiss) { Text("好") }
            } else {
                PicaTextButton(
                    enabled = !sending,
                    onClick = {
                        scope.launch {
                            sending = true
                            error = null
                            runCatching { pica.comments.report(comment.id) }
                                .onSuccess { reported = true }
                                .onFailure { error = it.message ?: "举报没提交成功" }
                            sending = false
                        }
                    },
                ) {
                    Text(if (sending) "提交中" else "举报")
                }
            }
        },
        dismissButton = if (reported) {
            null
        } else {
            { PicaTextButton(onClick = pop::dismiss) { Text("取消") } }
        },
    )
}

/**
 * 跳页弹窗：输入页码直接翻到那一页。
 *
 * 评论很多时不靠一页页往下续，知道大概在哪页就直接过去；本子列表也用同一个。
 */
@Composable
fun PagerJumpDialog(
    pager: Pager<*>,
    onDismiss: () -> Unit,
    onJumped: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val pop = rememberDialogPop(onDismiss)
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(pager.loadedPage.coerceAtLeast(1).toString()) }
    var jumping by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val page = text.trim().toIntOrNull()
    val valid = page != null && page >= 1 && (pager.lastPage == 0 || page <= pager.lastPage)

    PicaAlertDialog(
        onDismissRequest = pop::dismiss,
        modifier = pop.modifier,
        title = { Text(text = "跳到第几页", style = MiuixTheme.textStyles.title4) },
        text = {
            Column {
                val now = pager.loadedPage.coerceAtLeast(1)
                Text(
                    text = if (pager.lastPage > 0) "现在第 $now 页，共 ${pager.lastPage} 页" else "现在第 $now 页",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )
                Spacer(Modifier.height(10.dp))
                PicaTextField(
                    value = text,
                    // 只收数字，免得输入法带出别的字符。
                    onValueChange = { input ->
                        text = input.filter { it.isDigit() }.take(5)
                        error = null
                    },
                    singleLine = true,
                    placeholder = "页码",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = it,
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.error,
                    )
                }
            }
        },
        confirmButton = {
            PicaTextButton(
                enabled = valid && !jumping,
                onClick = {
                    val target = page ?: return@PicaTextButton
                    scope.launch {
                        jumping = true
                        error = null
                        pager.jumpTo(target)
                        val failure = pager.error
                        if (failure != null) {
                            error = failure
                        } else {
                            onJumped()
                            onDismiss()
                        }
                        jumping = false
                    }
                },
            ) {
                Text(if (jumping) "跳转中" else "跳转")
            }
        },
        dismissButton = {
            PicaTextButton(onClick = pop::dismiss) { Text("取消") }
        },
    )
}

/**
 * 回复弹窗：从底部升起来，带被回复内容的小卡片。
 *
 * 键盘顶上来时整块跟着上移（`imePadding`），不会把输入框压在键盘下面。
 */
@Composable
fun CommentReplyDialog(
    target: CommentReplyTarget,
    onDismiss: () -> Unit,
    onReplied: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var draft by remember(target) {
        mutableStateOf(target.mentionName?.takeIf { it.isNotBlank() }?.let { "@$it " } ?: "")
    }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // 弹出来就把键盘叫起来，省得再点一下输入框。
    LaunchedEffect(target) {
        delay(120)
        focus.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = colors.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "回复 ${target.root.user?.name.orEmpty().ifBlank { "这位绅士" }}",
                            style = MiuixTheme.textStyles.subtitle,
                            color = colors.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "取消",
                            style = MiuixTheme.textStyles.footnote1,
                            color = colors.onSurfaceSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    if (target.root.content.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.surfaceVariant.copy(alpha = 0.6f),
                        ) {
                            Text(
                                text = target.root.content,
                                style = MiuixTheme.textStyles.footnote1,
                                color = colors.onSurfaceSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    PicaTextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            error = null
                        },
                        placeholder = "回一句…",
                        textStyle = MiuixTheme.textStyles.body1,
                        cornerRadius = 14.dp,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                    error?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = it,
                            style = MiuixTheme.textStyles.footnote1,
                            color = colors.error,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(
                            enabled = draft.isNotBlank() && !sending,
                            cornerRadius = 18.dp,
                            onClick = {
                                val content = draft.trim()
                                if (content.isEmpty()) return@Button
                                scope.launch {
                                    sending = true
                                    error = null
                                    runCatching { pica.comments.reply(target.root.id, content) }
                                        .onSuccess {
                                            onReplied()
                                            onDismiss()
                                        }
                                        .onFailure { error = it.message ?: "没发出去" }
                                    sending = false
                                }
                            },
                        ) {
                            Text(if (sending) "发送中" else "发送")
                        }
                    }
                }
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
    onReply: ((CommentReplyTarget) -> Unit)? = null,
    topComments: List<Comment> = emptyList(),
    loadingText: String = "正在拉评论…",
    emptyText: String = "还没有评论",
) {
    // 置顶单独一份，普通列表里要是也出现同一个 id 就剔掉，免得 LazyColumn 撞 key。
    val pinned = topComments.distinctBy { it.id }
    val pinnedIds = pinned.mapTo(HashSet(pinned.size)) { it.id }
    val rows = pager.items.filterNot { it.id in pinnedIds }.distinctBy { it.id }

    if (pinned.isNotEmpty()) {
        items(pinned, key = { "pinned-${it.id}" }) { comment ->
            CommentRow(comment = comment, onReply = onReply, pinned = true)
        }
        // 置顶和普通评论之间拉一条淡分隔线，视觉上分开。
        item(key = "comment-pinned-divider") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 14.dp)
                    .height(1.dp)
                    .background(MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.14f)),
            )
        }
    }

    if (rows.isEmpty() && pinned.isEmpty()) {
        item {
            when {
                pager.error != null -> FailedBlock(pager.error.orEmpty(), onRetry)
                pager.loading -> LoadingBlock(loadingText)
                else -> EmptyBlock(emptyText)
            }
        }
    }
    items(rows, key = { it.id }) { comment ->
        CommentRow(comment = comment, onReply = onReply)
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
    val colors = MiuixTheme.colorScheme
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
            tint = if (liked) colors.primary else colors.onSurfaceSecondary,
            filled = liked,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (likes > 0) likes.toString() else "赞",
            style = MiuixTheme.textStyles.footnote2,
            color = if (liked) colors.primary else colors.onSurfaceSecondary,
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
    onReply: ((CommentReplyTarget) -> Unit)? = null,
    pinned: Boolean = false,
    /** 自己的评论没有头像可拿（历史评论接口不带作者），关掉就只排名字和正文，不留空头像框。 */
    showAvatar: Boolean = true,
) {
    val colors = MiuixTheme.colorScheme
    val avatar = comment.user?.avatar.url()
    var expanded by remember(comment.id) { mutableStateOf(false) }
    var introUser by remember(comment.id) { mutableStateOf<UserBasic?>(null) }
    var reporting by remember(comment.id) { mutableStateOf(false) }
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

    // 回复数变多了（多半是刚回完），展开着就把楼中楼重拉一遍，新回复立刻能看到。
    var seenReplies by remember(comment.id) { mutableStateOf(comment.childsCount) }
    LaunchedEffect(comment.childsCount) {
        if (comment.childsCount > seenReplies && expanded) replyPager.reloadFirstPage()
        seenReplies = comment.childsCount
    }

    PicaCard(
        modifier = Modifier
            .padding(bottom = 10.dp)
            .then(
                // 置顶那条描一圈主题色，扫一眼就知道是钉在最上面的。
                if (pinned) {
                    Modifier.border(
                        width = 1.dp,
                        color = colors.primary.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(28.dp),
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { if (hasReplies) expanded = !expanded },
                        onLongClick = { reporting = true },
                    ),
            ) {
                val user = comment.user
                if (showAvatar) {
                    if (avatar != null) {
                        UserAvatar(
                            avatarUrl = avatar,
                            frameUrl = user?.character,
                            size = 38.dp,
                            contentDescription = user?.name,
                            modifier = Modifier.then(
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
                }
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
                                style = MiuixTheme.textStyles.subtitle,
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
                                style = MiuixTheme.textStyles.footnote2,
                                color = colors.onSurfaceSecondary,
                            )
                        }
                    }
                    if (comment.content.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = comment.content,
                            style = MiuixTheme.textStyles.body1,
                            color = colors.onSurfaceSecondary,
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
                                style = MiuixTheme.textStyles.footnote2,
                                color = if (expanded) colors.primary else colors.onSurfaceSecondary,
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
                                style = MiuixTheme.textStyles.footnote2,
                                color = colors.primary,
                                modifier = Modifier
                                    .clickable { onReply(CommentReplyTarget(comment)) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
            if (reporting) {
                ReportCommentDialog(comment = comment, onDismiss = { reporting = false })
            }
            introUser?.let { user ->
                UserProfileDialog(
                    userId = user.id,
                    seedName = user.name,
                    seedAvatarUrl = user.avatar.url(),
                    seedTitle = user.title,
                    seedSlogan = user.slogan,
                    seedLevel = user.level,
                    seedExp = user.exp,
                    seedGender = user.gender,
                    seedFrame = user.character,
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
                    root = comment,
                    onReply = onReply,
                    // 没有头像那列时就不留那 50dp 的缩进。
                    modifier = Modifier.padding(start = if (showAvatar) 50.dp else 0.dp, top = 12.dp),
                )
            }
        }
    }
}

/** 楼中楼列表：加载中 / 拉失败重试 / 一条条排列 / 还有下一页时给个「更多回复」。 */
@Composable
private fun CommentReplies(
    pager: Pager<Comment>,
    root: Comment,
    onReply: ((CommentReplyTarget) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    val error = pager.error

    Column(modifier.fillMaxWidth()) {
        if (pager.items.isEmpty()) {
            when {
                error != null -> Text(
                    text = "回复没拉到，点一下重试",
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.error,
                    modifier = Modifier
                        .clickable { scope.launch { pager.retry() } }
                        .padding(vertical = 4.dp),
                )
                pager.loading -> Text(
                    text = "正在展开…",
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.onSurfaceSecondary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                else -> Unit
            }
        } else {
            pager.items.forEach { child ->
                // 楼里的回复也挂在主评论下面，只是输入框里先写上「@谁」。
                ReplyRow(
                    comment = child,
                    onReply = onReply?.let { reply ->
                        { reply(CommentReplyTarget(root, mentionName = child.user?.name)) }
                    },
                )
            }
            if (pager.hasMore) {
                Text(
                    text = if (pager.loading) "正在拉…" else "更多回复",
                    style = MiuixTheme.textStyles.footnote2,
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
    val colors = MiuixTheme.colorScheme
    val avatar = comment.user?.avatar.url()
    val user = comment.user
    var introUser by remember(comment.id) { mutableStateOf<UserBasic?>(null) }
    var reporting by remember(comment.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = { reporting = true })
            .padding(bottom = 10.dp),
    ) {
        if (avatar != null) {
            UserAvatar(
                avatarUrl = avatar,
                frameUrl = user?.character,
                size = 26.dp,
                contentDescription = comment.user?.name,
                modifier = Modifier.then(
                    if (user != null) Modifier.clickable { introUser = user } else Modifier,
                ),
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
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurface,
                    )
                    UserTitleBadge(comment.user?.title)
                }
                val time = relativeTime(comment.createdAt)
                if (time.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = time,
                        style = MiuixTheme.textStyles.footnote2,
                        color = colors.onSurfaceSecondary,
                    )
                }
            }
            if (comment.content.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = comment.content,
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LikeButton(comment, modifier = Modifier.padding(start = 0.dp))
                if (onReply != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "回复",
                        style = MiuixTheme.textStyles.footnote2,
                        color = colors.primary,
                        modifier = Modifier
                            .clickable(onClick = onReply)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }

    if (reporting) {
        ReportCommentDialog(comment = comment, onDismiss = { reporting = false })
    }

    introUser?.let { user ->
        UserProfileDialog(
            userId = user.id,
            seedName = user.name,
            seedAvatarUrl = user.avatar.url(),
            seedTitle = user.title,
            seedSlogan = user.slogan,
            seedLevel = user.level,
            seedExp = user.exp,
            seedGender = user.gender,
            seedFrame = user.character,
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
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Surface(color = colors.surface, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PicaTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        error = null
                    },
                    placeholder = placeholder,
                    textStyle = MiuixTheme.textStyles.body1,
                    cornerRadius = 20.dp,
                    maxLines = 4,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Button(
                    enabled = draft.isNotBlank() && !sending,
                    cornerRadius = 20.dp,
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
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.error,
                )
            }
        }
    }
}
