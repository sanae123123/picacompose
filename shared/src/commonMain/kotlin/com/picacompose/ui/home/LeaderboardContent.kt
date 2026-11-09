package com.picacompose.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.picacompose.data.pica
import com.picacompose.picaapi.model.ComicSummary
import com.picacompose.picaapi.model.Paged
import com.picacompose.picaapi.model.UserBasic
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.PicaImage
import com.picacompose.ui.components.UserAvatar
import com.picacompose.ui.components.UserProfileDialog
import com.picacompose.ui.components.UserTitleBadge
import com.picacompose.ui.components.genderLabel
import com.picacompose.ui.components.url
import kotlinx.coroutines.launch

/** 排行榜的两个大 tab。 */
private enum class LeaderboardTab(val label: String) {
    Popular("最熱門"),
    Knight("騎士榜"),
}

/** 最熱門里的时间范围，`tt` 是接口参数。 */
private enum class PopularRange(val label: String, val tt: String) {
    Day("本日", "H24"),
    Week("本周", "D7"),
    Month("本月", "D30"),
}

/**
 * 排行榜：最熱門（本日 / 本周 / 本月）+ 騎士榜。
 *
 * 两个榜单都是一口气给全（40 本 / 50 人），接口没有分页，所以这边也按一页处理；
 * 三个时间的分页器各留一份，来回切不会重拉。
 */
@Composable
fun LeaderboardContent(
    onBack: () -> Unit,
    /** 点一本打开详情页；第二个参数是那一行的变形 key。 */
    onOpenComic: (ComicSummary, String) -> Unit = { _, _ -> },
    /** 给每行挂变形源，点开时从那一行长成整屏详情。 */
    comicRowModifier: @Composable (String) -> Modifier = { Modifier },
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var tabOrdinal by rememberSaveable { mutableStateOf(LeaderboardTab.Popular.ordinal) }
    var rangeOrdinal by rememberSaveable { mutableStateOf(PopularRange.Day.ordinal) }
    val tab = LeaderboardTab.entries.getOrElse(tabOrdinal) { LeaderboardTab.Popular }
    val range = PopularRange.entries.getOrElse(rangeOrdinal) { PopularRange.Day }

    val popularPagers = remember {
        PopularRange.entries.associateWith { item ->
            Pager<ComicSummary>(
                load = { page ->
                    val comics = if (page == 1) pica.comics.leaderboard(timeType = item.tt) else emptyList()
                    Paged(
                        total = comics.size,
                        limit = comics.size,
                        page = 1,
                        pages = 1,
                        docs = comics,
                    )
                },
                itemKey = { it.id },
            )
        }
    }
    val knightPager = remember {
        Pager<UserBasic>(
            load = { page ->
                val users = if (page == 1) pica.comics.knightLeaderboard() else emptyList()
                Paged(total = users.size, limit = users.size, page = 1, pages = 1, docs = users)
            },
            itemKey = { it.id },
        )
    }

    val popularPager = popularPagers.getValue(range)
    // 三个时间各留一份滚动位置，切页时把它拨回顶部。
    val dayState = rememberLazyListState()
    val weekState = rememberLazyListState()
    val monthState = rememberLazyListState()
    val popularState = when (range) {
        PopularRange.Day -> dayState
        PopularRange.Week -> weekState
        PopularRange.Month -> monthState
    }
    val knightState = rememberLazyListState()
    LaunchedEffect(tab, range) {
        val current = if (tab == LeaderboardTab.Popular) popularState else knightState
        current.scrollToItem(0)
    }
    val activePager: Pager<*> = if (tab == LeaderboardTab.Popular) popularPager else knightPager
    LaunchedEffect(activePager) {
        if (activePager.items.isEmpty() && activePager.error == null) activePager.loadNext()
    }

    var tappedUser by remember { mutableStateOf<UserBasic?>(null) }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 22.dp, top = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                modifier = Modifier.size(40.dp).clickable(onClick = onBack),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    PicaGlyph(PicaIcon.Back, Modifier.size(22.dp), colors.onBackground)
                }
            }
            Spacer(Modifier.width(6.dp))
            PageHeader(title = "排行榜")
        }
        Spacer(Modifier.height(8.dp))
        PillTabs(
            labels = LeaderboardTab.entries.map { it.label },
            selected = tab.ordinal,
            onSelect = { tabOrdinal = it },
        )
        if (tab == LeaderboardTab.Popular) {
            PillTabs(
                labels = PopularRange.entries.map { it.label },
                selected = range.ordinal,
                onSelect = { rangeOrdinal = it },
            )
        }
        Spacer(Modifier.height(6.dp))
        if (tab == LeaderboardTab.Popular) {
            RankList(
                pager = popularPager,
                state = popularState,
                itemKey = { it.id },
                onRetry = { scope.launch { popularPager.retry() } },
                loadingText = "正在拉最熱門…",
                emptyText = "这个榜单是空的",
                modifier = Modifier.weight(1f),
            ) { comic, rank ->
                val key = "rankComic-${comic.id}"
                ComicRankRow(
                    rank = rank,
                    comic = comic,
                    modifier = comicRowModifier(key),
                    onOpen = { onOpenComic(comic, key) },
                )
            }
        } else {
            RankList(
                pager = knightPager,
                state = knightState,
                itemKey = { it.id },
                onRetry = { scope.launch { knightPager.retry() } },
                loadingText = "正在拉騎士榜…",
                emptyText = "骑士榜是空的",
                modifier = Modifier.weight(1f),
            ) { user, rank ->
                KnightRankRow(rank = rank, user = user, onOpen = { tappedUser = user })
            }
        }
    }

    tappedUser?.let { user ->
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
            onDismiss = { tappedUser = null },
        )
    }
}

/** 一排胶囊切换：选中的填色，没选中的留白。 */
@Composable
internal fun PillTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            Surface(
                shape = CircleShape,
                color = if (active) colors.secondaryContainer else colors.surface,
                tonalElevation = if (active) 0.dp else 1.dp,
                modifier = Modifier.clickable { onSelect(index) },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) colors.onSecondaryContainer else colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * 一页榜单：空着就转圈 / 报错重试 / 说一声空，有数据就是第 [itemContent] 条内容。
 *
 * [itemContent] 的第二个参数是从 1 开始的排名。
 */
@Composable
private fun <T> RankList(
    pager: Pager<T>,
    state: LazyListState,
    itemKey: (T) -> String,
    onRetry: () -> Unit,
    loadingText: String,
    emptyText: String,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T, Int) -> Unit,
) {
    val error = pager.error
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when {
            // 还没开始拉（LaunchedEffect 还没跑）也算转圈，别先闪一下空状态。
            pager.items.isEmpty() && (pager.loading || pager.loadedPage == 0) -> LoadingBlock(loadingText)
            pager.items.isEmpty() && error != null -> FailedBlock(error, onRetry)
            pager.items.isEmpty() -> EmptyBlock(emptyText)
            else -> LazyColumn(
                state = state,
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.widthIn(max = 460.dp).fillMaxSize(),
            ) {
                itemsIndexed(pager.items, key = { _, item -> itemKey(item) }) { index, item ->
                    itemContent(item, index + 1)
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

/** 最熱門的一条：名次 + 封面 + 标题 / 作者 / 浏览点赞。 */
@Composable
private fun ComicRankRow(
    rank: Int,
    comic: ComicSummary,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    PicaCard(modifier = modifier.clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = if (rank <= 3) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.width(26.dp),
            )
            PicaImage(
                url = comic.thumb.url(),
                contentDescription = comic.title,
                modifier = Modifier
                    .size(width = 62.dp, height = 82.dp)
                    .clip(MaterialTheme.shapes.medium),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = comic.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val author = comic.author
                if (!author.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${compactCount(comic.totalViews)} 瀏覽 · ${compactCount(comic.totalLikes)} 讚",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** 騎士榜的一条：名次 + 带框头像 + 昵称称号 + 等级 / 上传数。 */
@Composable
private fun KnightRankRow(
    rank: Int,
    user: UserBasic,
    onOpen: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    PicaCard(modifier = Modifier.clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = if (rank <= 3) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.width(26.dp),
            )
            UserAvatar(
                avatarUrl = user.avatar.url(),
                frameUrl = user.character,
                size = 46.dp,
                contentDescription = user.name,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = user.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                    )
                    UserTitleBadge(user.title)
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = listOfNotNull(
                        genderLabel(user.gender),
                        "Lv.${user.level}",
                        "上傳 ${compactCount(user.comicsUploaded)} 本",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** 数字太大就折成「1.2萬」「3.4億」，榜单里好读。 */
internal fun compactCount(value: Int): String {
    if (value < 10_000) return value.toString()
    return if (value >= 100_000_000) {
        "${value / 100_000_000}.${(value % 100_000_000) / 10_000_000}億"
    } else {
        "${value / 10_000}.${(value % 10_000) / 1_000}萬"
    }
}
