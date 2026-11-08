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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.picacompose.ui.components.url

/** 主页：资料卡 + 本子妹推荐 + 快捷入口。 */
@Composable
fun HomeContent(
    profile: UserProfile,
    onOpenCategory: () -> Unit,
    onOpenGame: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var collections by remember { mutableStateOf<List<CollectionGroup>?>(null) }
    var collectionError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    // 点封面先给个说法，详情页还没做。
    var tapped by remember { mutableStateOf<ComicSummary?>(null) }

    LaunchedEffect(reload) {
        collections = null
        collectionError = null
        runCatching { pica.comics.collections() }
            .onSuccess { collections = it }
            .onFailure { collectionError = it.message ?: "推荐没拉到" }
    }

    PageScaffold {
        item {
            PageHeader(
                title = "欢迎回来",
                subtitle = "今天的本子，先看推荐。",
            )
            Spacer(Modifier.height(18.dp))
        }
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
                Button(onClick = { reload++ }, shape = CircleShape) { Text("再试一次") }
            }

            groups == null -> item { LoadingBlock("正在拉推荐…") }
            groups.isEmpty() -> item { EmptyBlock("这次没拿到推荐") }
            else -> groups.forEach { group ->
                item(key = "collection-${group.title}") {
                    CollectionSection(
                        group = group,
                        onOpenComic = { tapped = it },
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickCard(
                    icon = PicaIcon.Grid,
                    title = "分类",
                    subtitle = "按题材找漫画",
                    onClick = onOpenCategory,
                    modifier = Modifier.weight(1f),
                )
                QuickCard(
                    icon = PicaIcon.Gamepad,
                    title = "游戏",
                    subtitle = "站里的小游戏",
                    onClick = onOpenGame,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    tapped?.let { comic ->
        AlertDialog(
            onDismissRequest = { tapped = null },
            title = { Text(comic.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    val author = comic.author
                    if (!author.isNullOrBlank()) {
                        Text(
                            text = author,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    val cats = comic.categories
                    if (cats.isNotEmpty()) {
                        Text(
                            text = cats.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        text = "详情页还没做，先看看封面。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { tapped = null }) { Text("好") }
            },
        )
    }
}

/** 一组官方合集：标题 + 能横着滑的封面。 */
@Composable
private fun CollectionSection(
    group: CollectionGroup,
    onOpenComic: (ComicSummary) -> Unit,
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
                ComicCover(comic = comic, onClick = { onOpenComic(comic) })
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
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.width(132.dp).clip(MaterialTheme.shapes.large).clickable(onClick = onClick)) {
        PicaImage(
            url = comic.thumb.url(),
            contentDescription = comic.title,
            modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
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
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(colors.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PicaGlyph(icon, Modifier.size(18.dp), colors.onSecondaryContainer)
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
