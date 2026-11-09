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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.picacompose.picaapi.model.ComicDetail
import com.picacompose.picaapi.model.Episode
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.PicaImage
import com.picacompose.ui.components.url
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 详情页的分区：章節列表和評論區各占一半的用处。 */
private enum class DetailTab(val label: String) {
    Chapters("章節"),
    Comments("評論"),
}

/**
 * 本子详情页：上面是封面和资料，下面切「章節 / 評論」。
 *
 * 章節列表和评论区都是分页的，各自滚到底续页；评论区整套复用 [CommentBits]，
 * 置顶评论、回复、跳页都跟着一起有。
 *
 * [comicId] 一换就是另一本，所有状态按 id 重建；[seedTitle] 是从列表点进来时先拿到的标题，
 * 详情还没拉到也能先把页头画出来。
 */
@Composable
fun ComicDetailContent(
    comicId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    seedTitle: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // 详情本身：一次请求，出错就整块重试。
    var detail by remember(comicId) { mutableStateOf<ComicDetail?>(null) }
    var detailError by remember(comicId) { mutableStateOf<String?>(null) }
    var detailLoading by remember(comicId) { mutableStateOf(true) }
    var reloadTick by remember(comicId) { mutableStateOf(0) }

    LaunchedEffect(comicId, reloadTick) {
        detailLoading = true
        detailError = null
        runCatching { pica.comics.detail(comicId) }
            .onSuccess { detail = it }
            .onFailure { detailError = it.message ?: "详情拉不下来" }
        detailLoading = false
    }

    val episodePager = remember(comicId) {
        Pager(
            load = { page -> pica.comics.episodes(comicId, page) },
            itemKey = { it.id },
        )
    }
    val commentFeed = rememberCommentFeed(comicId)

    var tabOrdinal by remember(comicId) { mutableStateOf(0) }
    val tab = DetailTab.entries[tabOrdinal.coerceIn(0, DetailTab.entries.lastIndex)]

    // 自己点的赞和收藏先在本地翻过来，等服务端回话再确认，点下去有反馈。
    var liked by remember(comicId) { mutableStateOf(false) }
    var likedCount by remember(comicId) { mutableStateOf<Int?>(null) }
    var favourited by remember(comicId) { mutableStateOf(false) }

    LaunchedEffect(detail) {
        detail?.let {
            liked = it.isLiked
            favourited = it.isFavourite
            likedCount = it.likesCount
        }
    }

    var replyTarget by remember { mutableStateOf<CommentReplyTarget?>(null) }
    var showJump by remember { mutableStateOf(false) }
    // 在读哪一話：非空就开阅读器。
    var reading by remember { mutableStateOf<Episode?>(null) }

    // 章節第一次进来看第一页；切走再切回来不重拉。
    LaunchedEffect(episodePager, tab) {
        if (tab == DetailTab.Chapters && episodePager.items.isEmpty()) episodePager.loadNext()
    }
    // 评论跟留言板一样：进来重拉第一页，点赞数会变。
    if (tab == DetailTab.Comments) {
        CommentAutoRefresh(commentFeed.pager)
        CommentLoadMore(listState, commentFeed.pager)
    }

    // 章節滚到底续页。
    LaunchedEffect(listState, episodePager, tab) {
        if (tab != DetailTab.Chapters) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (total > 0 && lastVisible >= total - 5 &&
                    episodePager.hasMore && episodePager.loading.not() && episodePager.error == null
                ) {
                    episodePager.loadNext()
                }
            }
    }

    Column(modifier.fillMaxSize().imePadding()) {
        PageScaffold(
            state = listState,
            modifier = Modifier.weight(1f),
            onRefresh = {
                reloadTick++
                when (tab) {
                    DetailTab.Chapters -> episodePager.reloadFirstPage()
                    DetailTab.Comments -> commentFeed.pager.reloadFirstPage()
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
                        PageHeader(title = detail?.title ?: seedTitle.orEmpty().ifBlank { "本子" })
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
            val loaded = detail
            when {
                loaded != null -> item(key = "detail-hero") {
                    DetailHero(
                        detail = loaded,
                        liked = liked,
                        likesCount = likedCount ?: loaded.likesCount,
                        favourited = favourited,
                        onLike = {
                            scope.launch {
                                runCatching { pica.comics.like(comicId) }.onSuccess { action ->
                                    val nowLiked = action != "unlike"
                                    if (nowLiked != liked) {
                                        liked = nowLiked
                                        likedCount = (likedCount ?: loaded.likesCount) +
                                            if (nowLiked) 1 else -1
                                    }
                                }
                            }
                        },
                        onFavourite = {
                            scope.launch {
                                runCatching { pica.comics.favourite(comicId) }.onSuccess { action ->
                                    favourited = action != "unfavourite"
                                }
                            }
                        },
                    )
                }

                detailError != null -> item(key = "detail-error") {
                    FailedBlock(detailError.orEmpty()) { reloadTick++ }
                }

                detailLoading -> item(key = "detail-loading") {
                    LoadingBlock("正在拉詳情…")
                }
            }

            item(key = "detail-tabs") {
                PillTabs(
                    labels = DetailTab.entries.map { it.label },
                    selected = tabOrdinal,
                    onSelect = { tabOrdinal = it },
                )
            }

            when (tab) {
                DetailTab.Chapters -> {
                    val chapters = episodePager.items
                    if (chapters.isEmpty()) {
                        item(key = "chapter-state") {
                            when {
                                episodePager.error != null -> FailedBlock(episodePager.error.orEmpty()) {
                                    scope.launch { episodePager.retry() }
                                }

                                episodePager.loading -> LoadingBlock("正在拉章節…")
                                else -> EmptyBlock("这本还没有章節")
                            }
                        }
                    }
                    items(count = chapters.size, key = { chapters[it].id }) { index ->
                        val episode = chapters[index]
                        EpisodeRow(episode = episode, onClick = { reading = episode })
                    }
                    if (chapters.isNotEmpty()) {
                        item(key = "chapter-footer") {
                            ListFooter(
                                loading = episodePager.loading,
                                error = episodePager.error,
                                hasMore = episodePager.hasMore,
                                onRetry = { scope.launch { episodePager.retry() } },
                            )
                        }
                    }
                }

                DetailTab.Comments -> commentItems(
                    pager = commentFeed.pager,
                    onRetry = { scope.launch { commentFeed.pager.retry() } },
                    onReply = { replyTarget = it },
                    topComments = commentFeed.topComments,
                )
            }
        }

        // 评论区固定在底下，跟留言板一致。
        if (tab == DetailTab.Comments) {
            CommentComposer(
                placeholder = "說點什麼…",
                onSend = { content ->
                    pica.comments.post(comicId, content)
                    commentFeed.pager.reloadFirstPage()
                },
            )
        }
    }

    if (showJump) {
        PagerJumpDialog(
            pager = commentFeed.pager,
            onDismiss = { showJump = false },
            onJumped = { scope.launch { listState.scrollToItem(0) } },
        )
    }

    replyTarget?.let { target ->
        CommentReplyDialog(
            target = target,
            onDismiss = { replyTarget = null },
            onReplied = { scope.launch { commentFeed.pager.reloadFirstPage() } },
        )
    }

    reading?.let { episode ->
        ReaderDialog(
            comicId = comicId,
            comicTitle = detail?.title ?: seedTitle.orEmpty().ifBlank { "本子" },
            episodes = episodePager.items,
            startOrder = episode.order,
            onDismiss = { reading = null },
        )
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
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
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
            val date = episode.updatedAt?.take(10).orEmpty()
            if (date.isNotBlank()) {
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
