package com.picacompose.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.picacompose.data.pica
import com.picacompose.picaapi.model.Category
import com.picacompose.ui.components.PicaImage
import com.picacompose.ui.components.rememberDialogPop
import com.picacompose.ui.components.url

/**
 * 分类页：照官方版式来 —— 上面一块「熱門分類」写死磁贴，下面「全部分類」走接口。
 *
 * 磁贴图和顺序都对齐官方 App：顺序是 支持嗶咔 / 排行榜 / 遊戲推薦 / 點擊救嗶咔 /
 * 小程式 / 留言版 / 最新更新 / 隨機本子；除「點擊救嗶咔」用广告图外，其余是官方资源包
 * 里那套 cat_*.jpg，直接打进 App（见 composeResources/drawable）。
 */
@Composable
fun CategoriesContent(
    state: LazyListState = rememberLazyListState(),
    onOpenForum: () -> Unit = {},
    onOpenGames: () -> Unit = {},
    onOpenLeaderboard: () -> Unit = {},
    forumTileModifier: Modifier = Modifier,
    leaderboardTileModifier: Modifier = Modifier,
    // isWeb 的分类磁贴：外层给每块挂变形源，点开由外层打开整页浏览器。
    webTileModifier: @Composable (String) -> Modifier = { Modifier },
    onOpenWeb: (Category) -> Unit = {},
) {
    var reload by remember { mutableStateOf(0) }
    var categories by remember { mutableStateOf<List<Category>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var adCover by remember { mutableStateOf<String?>(null) }
    // 还没接的入口点一下给个说明，别让磁贴看起来像坏的。
    var comingSoon by remember { mutableStateOf<String?>(null) }

    // 首屏和下拉刷新走同一段拉取逻辑。
    suspend fun loadCategories() {
        categories = null
        error = null
        runCatching { pica.meta.categories() }
            .onSuccess { categories = it }
            .onFailure { error = it.message ?: "分类没拉到" }
    }

    LaunchedEffect(reload) { loadCategories() }

    // 官方的「點擊救嗶咔」磁贴是轮播的广告图，这里取第一批广告的封面。
    LaunchedEffect(Unit) {
        runCatching { pica.meta.banners().firstOrNull()?.thumb.url() }
            .onSuccess { adCover = it }
    }

    PageScaffold(
        state = state,
        header = { PageHeader(title = "分类") },
        onRefresh = { loadCategories() },
    ) {
        item {
            SectionTitle("熱門分類")
            Spacer(Modifier.height(12.dp))
            val hotTiles = listOf(
                HotTile("支持嗶咔", art = "support") { comingSoon = "支持嗶咔" },
                HotTile(
                    label = "嗶咔排行榜",
                    art = "leaderboard",
                    modifier = leaderboardTileModifier,
                    onClick = onOpenLeaderboard,
                ),
                HotTile("遊戲推薦", art = "game", onClick = onOpenGames),
                HotTile("點擊救嗶咔", url = adCover) { comingSoon = "點擊救嗶咔" },
                HotTile("嗶咔小程式", art = "love_pica") { comingSoon = "嗶咔小程式" },
                HotTile("嗶咔留言版", art = "forum", modifier = forumTileModifier, onClick = onOpenForum),
                HotTile("最新更新", art = "latest") { comingSoon = "最新更新" },
                HotTile("隨機本子", art = "random") { comingSoon = "隨機本子" },
            )
            hotTiles.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { tile ->
                        HotTileCard(tile = tile, modifier = Modifier.weight(1f))
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        item {
            SectionTitle("全部分類")
            Spacer(Modifier.height(12.dp))
        }
        val list = categories
        when {
            error != null -> item { FailedBlock(error.orEmpty()) { reload++ } }
            list == null -> item { LoadingBlock("正在拉分类…") }
            list.isEmpty() -> item { EmptyBlock("接口没返回分类") }
            else -> {
                val rows = list.chunked(2)
                items(rows.size) { index ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rows[index].forEach { category ->
                            CategoryCard(
                                category = category,
                                selected = category.title == selected,
                                onClick = {
                                    val link = category.link
                                    if (category.isWeb && !link.isNullOrBlank()) {
                                        onOpenWeb(category)
                                    } else {
                                        selected = category.title
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .then(
                                        if (category.isWeb && !category.link.isNullOrBlank()) {
                                            webTileModifier(category.id)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            )
                        }
                        if (rows[index].size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                selected?.let { title ->
                    item {
                        Text(
                            text = "选中的是「$title」，漫画列表下一步接上。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }

    comingSoon?.let { title ->
        val pop = rememberDialogPop(onDismiss = { comingSoon = null })
        AlertDialog(
            onDismissRequest = pop::dismiss,
            modifier = pop.modifier,
            title = { Text(title) },
            text = { Text("这个入口还没接，下一步补上。") },
            confirmButton = {
                TextButton(onClick = pop::dismiss) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

/** 「熱門分類」里的一块磁贴，图是官方的方形图，标题在下面。 */
private data class HotTile(
    val label: String,
    val art: String? = null,
    val url: String? = null,
    val modifier: Modifier = Modifier,
    val onClick: () -> Unit,
)

@Composable
private fun HotTileCard(
    tile: HotTile,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.surface,
        tonalElevation = 1.dp,
        modifier = modifier.then(tile.modifier),
    ) {
        Column(modifier = Modifier.clickable(onClick = tile.onClick)) {
            val art = tile.art?.let { hotTileArt(it) }
            if (art != null) {
                Image(
                    painter = art,
                    contentDescription = tile.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
            } else {
                PicaImage(
                    url = tile.url,
                    contentDescription = tile.label,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
            }
            Text(
                text = tile.label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

/** 全部分类里的一张卡：接口封面 + 标题 + 描述。 */
@Composable
private fun CategoryCard(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) colors.primaryContainer else colors.surface,
        tonalElevation = if (selected) 0.dp else 1.dp,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.clickable(onClick = onClick)) {
            PicaImage(
                url = category.thumb.url(),
                contentDescription = category.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f),
            )
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val description = category.description?.takeIf { it.isNotBlank() && it != "未知" }
                if (!description.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) {
                            colors.onPrimaryContainer.copy(alpha = 0.8f)
                        } else {
                            colors.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
