package com.picacompose.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import com.picacompose.ui.theme.SkinTokens
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.picacompose.data.ReadingProgress
import com.picacompose.data.pica
import com.picacompose.data.readingProgressStore
import com.picacompose.picaapi.model.ComicDetail
import com.picacompose.picaapi.model.Creator
import com.picacompose.picaapi.model.Episode
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaImageViewer
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.PicaImage
import com.picacompose.ui.components.UserAvatar
import com.picacompose.ui.components.UserProfileDialog
import com.picacompose.ui.components.UserTitleBadge
import com.picacompose.ui.components.levelStartExp
import com.picacompose.ui.components.url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 详情页的分区：章节列表和评论区各占一半的用处。 */
internal enum class DetailTab(val label: String) {
    Chapters("章节"),
    Comments("评论"),
}

/**
 * 详情页状态：整个页面提到外面持有。
 *
 * 从详情页进阅读器再退回来，页面会被重建一次；数据和滚动位置放在这里，
 * 退回来还是刚才那一屏，不用重新拉、也不会跳回顶部。
 */
@Stable
class ComicDetailState(comicId: String) {
    val comicId: String = comicId
    val listState: LazyListState = LazyListState()

    var detail by mutableStateOf<ComicDetail?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var reloadTick by mutableIntStateOf(0)
        private set

    var tabOrdinal by mutableIntStateOf(0)

    // 自己点的赞和收藏先在本地翻过来，等服务端回话再确认，点下去有反馈。
    var liked by mutableStateOf(false)
    var likedCount by mutableStateOf<Int?>(null)
    var favourited by mutableStateOf(false)
    // 点赞/收藏没点上的提示，两秒后自己消失。
    var actionError by mutableStateOf<String?>(null)

    val episodePager: Pager<Episode> = Pager(
        load = { page -> pica.comics.episodes(comicId, page) },
        itemKey = { it.id },
    )
    val commentFeed: CommentFeed = CommentFeed(comicId)

    // 读到哪儿了：从本机存储里捞出来，读完一章实时更新。
    private val progressStore = readingProgressStore
    var progress by mutableStateOf(progressStore.load(comicId))
        private set

    /** 阅读器报进度时记一下：第几话、第几页。 */
    fun recordProgress(order: Int, page: Int, pages: Int) {
        if (order <= 0) return
        val next = ReadingProgress(
            comicId = comicId,
            order = order,
            page = page.coerceAtLeast(0),
            pages = pages,
            title = detail?.title ?: progress?.title,
        )
        progress = next
        progressStore.save(next)
    }

    private var started = false

    suspend fun ensureLoaded() {
        if (started) return
        started = true
        try {
            load()
        } catch (e: CancellationException) {
            started = false
            throw e
        }
    }

    suspend fun load() {
        loading = true
        error = null
        catchingLoad { pica.comics.detail(comicId) }
            .onSuccess {
                detail = it
                // 进过详情页就算浏览过一次，记住封面和作者，我的页「浏览历史」列表要用。
                progressStore.recordOpen(
                    comicId = comicId,
                    title = it.title,
                    cover = it.thumb.url(),
                    author = it.author,
                )
                liked = it.isLiked
                likedCount = it.likesCount
                favourited = it.isFavourite
            }
            .onFailure { error = it.message ?: "详情拉不下来" }
        loading = false
    }

    fun retry() {
        reloadTick++
    }
}

@Composable
fun rememberComicDetailState(comicId: String): ComicDetailState =
    remember(comicId) { ComicDetailState(comicId) }

/**
 * 本子详情页：上面是封面和资料，下面切「章节 / 评论」。
 *
 * 章节列表和评论区都是分页的，各自滚到底续页；评论区整套复用 [CommentBits]，
 * 置顶评论、回复、跳页都跟着一起有。
 */
@Composable
fun ComicDetailContent(
    state: ComicDetailState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    seedTitle: String? = null,
    /** 章节行的变形源 key，点开时从那一行撑开成阅读器。 */
    episodeRowModifier: @Composable (String) -> Modifier = { Modifier },
    onOpenEpisode: (Episode, String) -> Unit = { _, _ -> },
    /** 「继续阅读」：上次读到第几话、第几页。 */
    onResume: (order: Int, page: Int) -> Unit = { _, _ -> },
    /** 点本子标签：按这个标签看同类本子。 */
    onSearchTag: (String) -> Unit = {},
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    val listState = state.listState
    val tab = DetailTab.entries[state.tabOrdinal.coerceIn(0, DetailTab.entries.lastIndex)]

    LaunchedEffect(state) { state.ensureLoaded() }
    LaunchedEffect(state.reloadTick) { if (state.reloadTick > 0) state.load() }

    // 章节第一次进来看第一页；切走再切回来不重拉。
    LaunchedEffect(state.episodePager, tab) {
        if (tab == DetailTab.Chapters && state.episodePager.items.isEmpty()) {
            state.episodePager.loadNext()
        }
    }
    // 评论跟留言板一样：进来重拉第一页，点赞数会变。
    if (tab == DetailTab.Comments) {
        CommentAutoRefresh(state.commentFeed.pager)
        CommentLoadMore(listState, state.commentFeed.pager)
    }

    var replyTarget by remember { mutableStateOf<CommentReplyTarget?>(null) }
    var showJump by remember { mutableStateOf(false) }
    // 点封面开全屏看图；点上传骑士那一栏看用户简介（有 id 才拉得到资料）。
    var coverViewer by remember { mutableStateOf<String?>(null) }
    var showUploader by remember { mutableStateOf(false) }

    // 章节滚到底续页。
    LaunchedEffect(listState, state.episodePager, tab) {
        if (tab != DetailTab.Chapters) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (total > 0 && lastVisible >= total - 5 &&
                    state.episodePager.hasMore &&
                    state.episodePager.loading.not() &&
                    state.episodePager.error == null
                ) {
                    state.episodePager.loadNext()
                }
            }
    }

    // 点赞/收藏的失败提示，两秒后自己收起来。
    LaunchedEffect(state.actionError) {
        if (state.actionError != null) {
            delay(2400)
            state.actionError = null
        }
    }

    Column(modifier.fillMaxSize().imePadding()) {
        PageScaffold(
            state = listState,
            modifier = Modifier.weight(1f),
            onRefresh = {
                state.retry()
                when (tab) {
                    DetailTab.Chapters -> state.episodePager.reloadFirstPage()
                    DetailTab.Comments -> state.commentFeed.pager.reloadFirstPage()
                }
            },
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
                    Box(Modifier.weight(1f)) {
                        PageHeader(
                            title = state.detail?.title
                                ?: seedTitle.orEmpty().ifBlank { "本子" },
                        )
                    }
                    if (tab == DetailTab.Comments) {
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
                }
            },
        ) {
            val loaded = state.detail
            when {
                loaded != null -> item(key = "detail-hero") {
                    DetailHero(
                        detail = loaded,
                        liked = state.liked,
                        likesCount = state.likedCount ?: loaded.likesCount,
                        favourited = state.favourited,
                        actionError = state.actionError,
                        onOpenCover = { coverViewer = it },
                        onOpenUploader = { showUploader = true },
                        onSearchTag = onSearchTag,
                        onLike = {
                            // 先翻成点过的样子，服务端回话再确认；再点一次就是取消。
                            val base = state.likedCount ?: loaded.likesCount
                            val next = !state.liked
                            state.liked = next
                            state.likedCount = base + if (next) 1 else -1
                            scope.launch {
                                postWithRetry { pica.comics.like(state.comicId) }
                                    .onSuccess { action ->
                                        val server = !action.isCancelAction()
                                        if (server != next) {
                                            state.liked = server
                                            state.likedCount = base + if (server) 1 else -1
                                        }
                                    }
                                    .onFailure {
                                        state.liked = !next
                                        state.likedCount = base
                                        state.actionError = "赞没点上，等一下再点一次"
                                    }
                            }
                        },
                        onFavourite = {
                            val next = !state.favourited
                            state.favourited = next
                            scope.launch {
                                postWithRetry { pica.comics.favourite(state.comicId) }
                                    .onSuccess { action ->
                                        val server = !action.isCancelAction()
                                        if (server != next) state.favourited = server
                                    }
                                    .onFailure {
                                        state.favourited = !next
                                        state.actionError = "收藏没成功，等一下再点一次"
                                    }
                            }
                        },
                    )
                }

                state.error != null -> item(key = "detail-error") {
                    FailedBlock(state.error.orEmpty()) { state.retry() }
                }

                state.loading -> item(key = "detail-loading") {
                    LoadingBlock("正在拉详情…")
                }
            }

            if (loaded != null) {
                item(key = "detail-resume") {
                    val savedProgress = state.progress
                    ResumeCard(
                        progress = savedProgress,
                        onResume = {
                            if (savedProgress != null) {
                                onResume(savedProgress.order, savedProgress.page)
                            } else {
                                // 还没读过：从第一话开始。章节没拉下来就先拉一页再开，
                                // 免得猜个序号结果是空的。
                                scope.launch {
                                    val pager = state.episodePager
                                    if (pager.items.isEmpty()) pager.loadNext()
                                    // 章节是倒序给的，第一话在最后一页里。
                                    if (pager.lastPage > 1) pager.jumpTo(pager.lastPage)
                                    pager.items.minOfOrNull { it.order }
                                        ?.let { onResume(it, 0) }
                                }
                            }
                        },
                    )
                }
            }

            item(key = "detail-tabs") {
                PillTabs(
                    labels = DetailTab.entries.map { it.label },
                    selected = state.tabOrdinal,
                    onSelect = { state.tabOrdinal = it },
                )
            }

            when (tab) {
                DetailTab.Chapters -> {
                    val chapters = state.episodePager.items
                    if (chapters.isEmpty()) {
                        item(key = "chapter-state") {
                            when {
                                state.episodePager.error != null ->
                                    FailedBlock(state.episodePager.error.orEmpty()) {
                                        scope.launch { state.episodePager.retry() }
                                    }

                                state.episodePager.loading -> LoadingBlock("正在拉章节…")
                                else -> EmptyBlock("这本还没有章节")
                            }
                        }
                    }
                    items(count = chapters.size, key = { chapters[it].id }) { index ->
                        val episode = chapters[index]
                        val key = "episode-${episode.order}"
                        EpisodeRow(
                            episode = episode,
                            resumed = state.progress?.order == episode.order,
                            modifier = episodeRowModifier(key),
                            onClick = { onOpenEpisode(episode, key) },
                        )
                    }
                    if (chapters.isNotEmpty()) {
                        item(key = "chapter-footer") {
                            ListFooter(
                                loading = state.episodePager.loading,
                                error = state.episodePager.error,
                                hasMore = state.episodePager.hasMore,
                                onRetry = { scope.launch { state.episodePager.retry() } },
                            )
                        }
                    }
                }

                DetailTab.Comments -> commentItems(
                    pager = state.commentFeed.pager,
                    onRetry = { scope.launch { state.commentFeed.pager.retry() } },
                    onReply = { replyTarget = it },
                    topComments = state.commentFeed.topComments,
                )
            }
        }

        // 评论区固定在底下，跟留言板一致。
        if (tab == DetailTab.Comments) {
            CommentComposer(
                placeholder = "说点什么…",
                onSend = { content ->
                    pica.comments.post(state.comicId, content)
                    state.commentFeed.pager.reloadFirstPage()
                },
            )
        }
    }

    if (showJump) {
        PagerJumpDialog(
            pager = state.commentFeed.pager,
            onDismiss = { showJump = false },
            onJumped = { scope.launch { listState.scrollToItem(0) } },
        )
    }

    coverViewer?.let { url ->
        PicaImageViewer(url = url, onDismiss = { coverViewer = null })
    }

    if (showUploader) {
        state.detail?.creator?.let { creator ->
            UserProfileDialog(
                userId = creator.id,
                seedName = creator.name,
                seedAvatarUrl = creator.avatar.url(),
                seedTitle = creator.title,
                seedLevel = creator.level,
                seedExp = creator.exp,
                onDismiss = { showUploader = false },
            )
        }
    }

    replyTarget?.let { target ->
        CommentReplyDialog(
            target = target,
            onDismiss = { replyTarget = null },
            onReplied = { scope.launch { state.commentFeed.pager.reloadFirstPage() } },
        )
    }
}

/** 「继续阅读」：记住上次读到第几话第几页，点一下接着看；没读过就从第一话开始。 */
@Composable
private fun ResumeCard(
    progress: ReadingProgress?,
    onResume: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(SkinTokens.rowRadius),
        color = colors.primary,
        contentColor = colors.onPrimary,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable(onClick = onResume),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (progress == null) "开始阅读" else "继续阅读",
                    style = MiuixTheme.textStyles.button,
                    color = colors.onPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (progress == null) {
                        "从第一话开始看"
                    } else {
                        buildString {
                            append("上次读到 第 ${progress.order} 话")
                            if (progress.page > 0) append(" · 第 ${progress.page + 1} 页")
                        }
                    },
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onPrimary.copy(alpha = 0.85f),
                )
            }
            Text(
                text = if (progress == null) "开始" else "接着看",
                style = MiuixTheme.textStyles.button,
                color = colors.onSecondaryContainer,
            )
            PicaGlyph(
                icon = PicaIcon.ArrowRight,
                modifier = Modifier.padding(start = 4.dp).size(16.dp),
                tint = colors.onSecondaryContainer,
            )
        }
    }
}

/** 详情页头：封面 + 标题 / 作者 / 数据 / 分类 + 点赞收藏。 */
@Composable
private fun DetailHero(
    detail: ComicDetail,
    liked: Boolean,
    likesCount: Int,
    favourited: Boolean,
    actionError: String?,
    onOpenCover: (String) -> Unit,
    onOpenUploader: () -> Unit,
    onSearchTag: (String) -> Unit,
    onLike: () -> Unit,
    onFavourite: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val cover = detail.thumb.url()
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PicaImage(
                url = cover,
                contentDescription = detail.title,
                modifier = Modifier
                    .width(112.dp)
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = cover != null) { cover?.let(onOpenCover) },
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = detail.title,
                    style = MiuixTheme.textStyles.title4,
                    color = colors.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val author = detail.author?.takeIf { it.isNotBlank() }
                if (author != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = author,
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = buildList {
                        add(if (detail.finished) "已完结" else "连载中")
                        add("${detail.episodesCount} 话")
                        add("${compactCount(detail.totalViews)} 浏览")
                    }.joinToString(" · "),
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.onSurfaceSecondary,
                )
                val chips = detail.categories.take(3)
                if (chips.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        chips.forEach { label ->
                            Text(
                                text = "#$label",
                                style = MiuixTheme.textStyles.footnote2,
                                color = colors.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                // 本子的标签整排摆出来，点一个就按它筛同类本子。
                val tags = detail.tags.take(20)
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        tags.forEach { tag ->
                            // 点一下按这个标签看同类本子。做成有底色的小块，点中的范围比一行小字大。
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.primary.copy(alpha = 0.10f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onSearchTag(tag) },
                            ) {
                                Text(
                                    text = "#$tag",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = colors.primary,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionPill(
                icon = PicaIcon.Like,
                label = "赞 ${compactCount(likesCount)}",
                active = liked,
                onClick = onLike,
            )
            ActionPill(
                icon = PicaIcon.Star,
                label = if (favourited) "已收藏" else "收藏",
                active = favourited,
                onClick = onFavourite,
            )
        }
        val failure = actionError
        if (failure != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = failure,
                style = MiuixTheme.textStyles.footnote2,
                color = colors.error,
            )
        }

        val description = detail.description?.trim()
        if (!description.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(SkinTokens.rowRadius),
                color = colors.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = description,
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        detail.creator?.let { creator ->
            Spacer(Modifier.height(12.dp))
            UploaderCard(creator = creator, onClick = onOpenUploader)
        }
    }
}

/** 上传骑士：头像 + 昵称 + 称号 + 等级经验条，点一下看完整简介。 */
@Composable
private fun UploaderCard(
    creator: Creator,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val level = creator.level.coerceAtLeast(1)
    val start = levelStartExp(level)
    val next = levelStartExp(level + 1)
    val fraction = ((creator.exp - start).toFloat() / (next - start).coerceAtLeast(1))
        .coerceIn(0f, 1f)

    Surface(
        shape = RoundedCornerShape(SkinTokens.rowRadius),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(
                avatarUrl = creator.avatar.url(),
                frameUrl = null,
                size = 44.dp,
                contentDescription = creator.name.ifBlank { "上传骑士" },
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = creator.name.ifBlank { "匿名骑士" },
                        style = MiuixTheme.textStyles.subtitle,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (creator.verified) {
                        Spacer(Modifier.width(4.dp))
                        PicaGlyph(
                            icon = PicaIcon.Check,
                            modifier = Modifier.size(14.dp),
                            tint = colors.primary,
                        )
                    }
                    UserTitleBadge(creator.title, Modifier.padding(start = 6.dp))
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "上传骑士 · Lv.$level · 经验 ${creator.exp}",
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.onSurfaceSecondary,
                )
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(
colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = colors.primary, backgroundColor = colors.surfaceVariant),
progress = fraction,
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                )
            }
            Spacer(Modifier.width(8.dp))
            PicaGlyph(
                icon = PicaIcon.ArrowRight,
                modifier = Modifier.size(16.dp),
                tint = colors.onSurfaceSecondary,
            )
        }
    }
}

/** 点赞 / 收藏那两粒胶囊，点过就填色。 */
@Composable
private fun ActionPill(
    icon: PicaIcon,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (active) colors.primaryContainer else colors.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PicaGlyph(
                icon = icon,
                // 点过是实心、没点过是空心，一眼能分出来。
                filled = active,
                modifier = Modifier.size(16.dp),
                tint = if (active) colors.onPrimaryContainer else colors.onSurfaceSecondary,
            )
            Text(
                text = label,
                style = MiuixTheme.textStyles.button,
                color = if (active) colors.onPrimaryContainer else colors.onSurfaceSecondary,
            )
        }
    }
}

/** POST 偶尔被限流（error 1023），等一下再试一次，省得用户以为没点上。 */
private suspend fun postWithRetry(block: suspend () -> String?): Result<String?> {
    val first = runCatching { block() }
    if (first.isSuccess) return first
    delay(1100)
    return runCatching { block() }
}

/** 服务端回的 action：`like` / `unlike`、`favourite` / `unfavourite`，`un` 开头就是取消。 */
private fun String?.isCancelAction(): Boolean =
    this?.trim()?.lowercase()?.startsWith("un") == true

/** 章节一行：序号 + 标题 + 更新日期。 */
@Composable
private fun EpisodeRow(
    episode: Episode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    resumed: Boolean = false,
) {
    val colors = MiuixTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(SkinTokens.rowRadius),
        color = colors.surface,
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "第 ${episode.order} 话",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.primary,
                modifier = Modifier.width(64.dp),
            )
            Text(
                text = episode.title.ifBlank { "第 ${episode.order} 话" },
                style = MiuixTheme.textStyles.body1,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (resumed) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "读到这里",
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.primary,
                )
            }
            val date = episode.updatedAt?.take(10).orEmpty()
            if (date.isNotBlank() && !resumed) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = date,
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.onSurfaceSecondary,
                )
            }
        }
    }
}
