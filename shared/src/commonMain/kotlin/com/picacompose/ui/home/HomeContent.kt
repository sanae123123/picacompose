package com.picacompose.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.picacompose.data.pica
import com.picacompose.picaapi.model.CollectionGroup
import com.picacompose.picaapi.model.ComicSummary
import com.picacompose.picaapi.model.UserProfile
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.PicaImage
import com.picacompose.ui.components.rememberDialogPop
import com.picacompose.ui.components.url

/** 主页推荐流的状态。放在 [HomeContent] 外面，进出消息页时不用重拉、滚动位置也还在。 */
@Stable
class HomeFeedState {
    var collections by mutableStateOf<List<CollectionGroup>?>(null)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set
    var reload by mutableStateOf(0)
        internal set

    fun retry() {
        reload++
    }

    /** 下拉刷新：先清掉旧的，让 [load] 真去拉一次。 */
    internal suspend fun refresh() {
        collections = null
        load()
    }

    internal suspend fun load() {
        // 已经拉到了就不动，免得每次回到首页都闪一下 loading。
        if (collections != null) return
        error = null
        catchingLoad { pica.comics.collections() }
            .onSuccess { collections = it }
            .onFailure { error = it.message ?: "推荐没拉到" }
    }
}

@Composable
fun rememberHomeFeedState(): HomeFeedState = remember { HomeFeedState() }

/** 主页：资料卡 + 本子妹推荐 + 快捷入口。 */
@Composable
fun HomeContent(
    profile: UserProfile,
    onOpenMessages: () -> Unit,
    messagesUnread: Boolean = false,
    messagesCardModifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    feed: HomeFeedState = rememberHomeFeedState(),
    /** 点封面打开详情页；第二个参数是这块封面的变形 key。 */
    onOpenComic: (ComicSummary, String) -> Unit = { _, _ -> },
    /** 给每张封面挂变形源，点开时从它长成整屏详情。 */
    comicCoverModifier: @Composable (String) -> Modifier = { Modifier },
) {
    val colors = MaterialTheme.colorScheme
    val collections = feed.collections
    val collectionError = feed.error
    var showAnnouncements by remember { mutableStateOf(false) }

    LaunchedEffect(feed.reload) { feed.load() }

    PageScaffold(
        state = listState,
        header = {
            PageHeader(title = "欢迎回来")
        },
        onRefresh = { feed.refresh() },
    ) {
        item {
            PicaCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 真头像盖在首字母上；没头像或没加载出来时露出首字母。
                    Surface(
                        shape = CircleShape,
                        color = colors.primaryContainer,
                        modifier = Modifier.size(52.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = profile.name.take(1).ifBlank { "P" },
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.onPrimaryContainer,
                            )
                            val avatarUrl = profile.avatar.url()
                            if (avatarUrl != null) {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = profile.name.ifBlank { "已登录" },
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoChip("Lv.${profile.level}")
                    InfoChip("经验 ${profile.exp}")
                    if (profile.isPunched) InfoChip("今日已签到")
                }
                val slogan = profile.slogan
                if (!slogan.isNullOrBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = slogan,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        val groups = collections
        when {
            collectionError != null -> item {
                Text(
                    text = collectionError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = feed::retry, shape = CircleShape) { Text("再试一次") }
            }

            groups == null -> item { LoadingBlock("正在拉推荐…") }
            groups.isEmpty() -> item { EmptyBlock("这次没拿到推荐") }
            else -> groups.forEach { group ->
                item(key = "collection-${group.title}") {
                    CollectionSection(
                        group = group,
                        onOpenComic = onOpenComic,
                        coverModifier = comicCoverModifier,
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickCard(
                    icon = PicaIcon.Notice,
                    title = "公告",
                    subtitle = "站里的通知",
                    onClick = { showAnnouncements = true },
                    modifier = Modifier.weight(1f),
                )
                QuickCard(
                    icon = PicaIcon.Mail,
                    title = "消息",
                    subtitle = "回复和通知",
                    badge = messagesUnread,
                    onClick = onOpenMessages,
                    // 点下去这张卡会长成消息页，bound 的 key 和消息页那边对上。
                    modifier = Modifier.weight(1f).then(messagesCardModifier),
                )
            }
        }
    }

    if (showAnnouncements) {
        AnnouncementsDialog(onDismiss = { showAnnouncements = false })
    }

}

/** 一组官方合集：标题 + 能横着滑的封面。 */
@Composable
private fun CollectionSection(
    group: CollectionGroup,
    onOpenComic: (ComicSummary, String) -> Unit,
    coverModifier: @Composable (String) -> Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = group.title.orEmpty().ifBlank { "本子妹推荐" },
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${group.comics.size} 本",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(group.comics, key = { it.id }) { comic ->
                // 同一本可能在两块合集里都出现，key 带上合集名才不撞。
                val key = "homeComic-${group.title}-${comic.id}"
                ComicCover(
                    comic = comic,
                    modifier = coverModifier(key),
                    onClick = { onOpenComic(comic, key) },
                )
            }
        }
        Spacer(Modifier.height(22.dp))
    }
}

/** 推荐位的一张封面：图 + 标题 + 作者。 */
@Composable
private fun ComicCover(
    comic: ComicSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // 圆角只裁封面，别把下面作者的左下角一起削掉。
    Column(modifier = modifier.width(132.dp).clickable(onClick = onClick)) {
        PicaImage(
            url = comic.thumb.url(),
            contentDescription = comic.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(MaterialTheme.shapes.large),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = comic.title,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val author = comic.author
        if (!author.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = author,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QuickCard(
    icon: PicaIcon,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surface,
        tonalElevation = 1.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp),
        ) {
            Box(Modifier.size(34.dp)) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(colors.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    PicaGlyph(icon, Modifier.size(18.dp), colors.onSecondaryContainer)
                }
                // 有新消息：图标右上角点个红点。
                if (badge) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(9.dp)
                            .background(colors.error, CircleShape),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
        }
    }
}
