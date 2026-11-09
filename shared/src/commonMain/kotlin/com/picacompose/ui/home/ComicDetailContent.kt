package com.picacompose.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.picacompose.picaapi.model.Episode
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.PicaImage
import com.picacompose.ui.components.url
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 详情页的分区：章節列表和評論區各占一半的用处。 */
internal enum class DetailTab(val label: String) {
    Chapters("章節"),
    Comments("評論"),
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

    val episodePager: Pager<Episode> = Pager(
        load = { page -> pica.comics.episodes(comicId, page) },
        itemKey = { it.id },
    )
    val commentFeed: CommentFeed = CommentFeed(comicId)

    // 读到哪儿了：从本机存储里捞出来，读完一章实时更新。
    private val progressStore = readingProgressStore
    var progress by mutableStateOf(progressStore.load(comicId))
        private set

    /** 阅读器报进度时记一下：第幾話、第几页。 */
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
        load()
    }

    suspend fun load() {
        loading = true
        error = null
        runCatching { pica.comics.detail(comicId) }
            .onSuccess {
                detail = it
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
 * 本子详情页：上面是封面和资料，下面切「章節 / 評論」。
 *
 * 章節列表和评论区都是分页的，各自滚到底续页；评论区整套复用 [CommentBits]，
 * 置顶评论、回复、跳页都跟着一起有。
 */
@Composable
fun ComicDetailContent(
    state: ComicDetailState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    seedTitle: String? = null,
    /** 章節行的变形源 key，点开时从那一行撑开成阅读器。 */
    episodeRowModifier: @Composable (String) -> Modifier = { Modifier },
    onOpenEpisode: (Episode, String) -> Unit = { _, _ -> },
    /** 「繼續閱讀」：上次读到第幾話、第几页。 */
    onResume: (order: Int, page: Int) -> Unit = { _, _ -> },
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val listState = state.listState
    val tab = DetailTab.entries[state.tabOrdinal.coerceIn(0, DetailTab.entries.lastIndex)]

    LaunchedEffect(state) { state.ensureLoaded() }
    LaunchedEffect(state.reloadTick) { if (state.reloadTick > 0) state.load() }

    // 章節第一次进来看第一页；切走再切回来不重拉。
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

    // 章節滚到底续页。
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
                            style = MaterialTheme.typography.labelMedium,
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
                        onLike = {
                            scope.launch {
                                runCatching { pica.comics.like(state.comicId) }.onSuccess { action ->
                                    val nowLiked = action != "unlike"
                                    if (nowLiked != state.liked) {
                                        state.liked = nowLiked
                                        state.likedCount = (state.likedCount ?: loaded.likesCount) +
                                            if (nowLiked) 1 else -1
                                    }
                                }
                            }
                        },
                        onFavourite = {
                            scope.launch {
                                runCatching { pica.comics.favourite(state.comicId) }
                                    .onSuccess { action ->
                                        state.favourited = action != "unfavourite"
                                    }
                            }
                        },
                    )
                }

                state.error != null -> item(key = "detail-error") {
                    FailedBlock(state.error.orEmpty()) { state.retry() }
                }

                state.loading -> item(key = "detail-loading") {
                    LoadingBlock("正在拉詳情…")
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
                                // 还没读过：从第一話开始。章節没拉下来就先拉一页再开，
                                // 免得猜个序号结果是空的。
                                scope.launch {
                                    val pager = state.episodePager
                                    if (pager.items.isEmpty()) pager.loadNext()
                                    // 章節是倒序给的，第一話在最后一页里。
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

                                state.episodePager.loading -> LoadingBlock("正在拉章節…")
                                else -> EmptyBlock("这本还没有章節")
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
                placeholder = "說點什麼…",
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

    replyTarget?.let { target ->
        CommentReplyDialog(
            target = target,
            onDismiss = { replyTarget = null },
            onReplied = { scope.launch { state.commentFeed.pager.reloadFirstPage() } },
        )
    }
}

/** 「繼續閱讀」：记住上次读到第幾話第几页，点一下接着看；没读过就从第一話开始。 */
@Composable
private fun ResumeCard(
    progress: ReadingProgress?,
    onResume: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable(onClick = onResume),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (progress == null) "開始閱讀" else "繼續閱讀",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSecondaryContainer,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (progress == null) {
                        "從第一話開始看"
                    } else {
                        buildString {
                            append("上次讀到 第 ${progress.order} 話")
                            if (progress.page > 0) append(" · 第 ${progress.page + 1} 頁")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
            Text(
                text = if (progress == null) "開始" else "接着看",
                style = MaterialTheme.typography.labelLarge,
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
    onLike: () -> Unit,
    onFavourite: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PicaImage(
                url = detail.thumb.url(),
                contentDescription = detail.title,
                modifier = Modifier
                    .width(112.dp)
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = detail.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val author = detail.author?.takeIf { it.isNotBlank() }
                if (author != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = buildList {
                        add(if (detail.finished) "已完結" else "連載中")
                        add("${detail.episodesCount} 話")
                        add("${compactCount(detail.totalViews)} 瀏覽")
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                val chips = detail.categories.take(3)
                if (chips.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        chips.forEach { label ->
                            Text(
                                text = "#$label",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionPill(
                label = "讚 ${compactCount(likesCount)}",
                active = liked,
                onClick = onLike,
            )
            ActionPill(
                label = if (favourited) "已收藏" else "收藏",
                active = favourited,
                onClick = onFavourite,
            )
        }

        val description = detail.description?.trim()
        if (!description.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = MaterialTheme.shapes.large,
                color = colors.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

/** 点赞 / 收藏那两粒胶囊，点过就填色。 */
@Composable
private fun ActionPill(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (active) colors.secondaryContainer else colors.surface,
        tonalElevation = if (active) 0.dp else 1.dp,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PicaGlyph(
                icon = PicaIcon.Like,
                modifier = Modifier.size(16.dp),
                tint = if (active) colors.onSecondaryContainer else colors.onSurfaceVariant,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) colors.onSecondaryContainer else colors.onSurfaceVariant,
            )
        }
    }
}

/** 章節一行：序號 + 標題 + 更新日期。 */
@Composable
private fun EpisodeRow(
    episode: Episode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    resumed: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.surface,
        tonalElevation = 1.dp,
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
                text = "第 ${episode.order} 話",
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary,
                modifier = Modifier.width(64.dp),
            )
            Text(
                text = episode.title.ifBlank { "第 ${episode.order} 話" },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (resumed) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "讀到這裡",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                )
            }
            val date = episode.updatedAt?.take(10).orEmpty()
            if (date.isNotBlank() && !resumed) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
