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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.picacompose.data.pica
import com.picacompose.picaapi.model.Category
import com.picacompose.ui.components.PicaImage
import com.picacompose.ui.components.url

/**
 * 分类页：照官方版式来 —— 上面一块「熱門分類」写死磁贴，下面「全部分類」走接口。
 *
 * 磁贴图和顺序都对齐官方 App：顺序是 支持嗶咔 / 排行榜 / 遊戲推薦 / 點擊救嗶咔 /
 * 小程式 / 留言版 / 最新更新 / 隨機本子；除「點擊救嗶咔」用广告图外，其余是官方资源包
 * 里那套 cat_*.jpg，直接打进 App（见 composeResources/drawable）。
 */
/**
 * 网页磁贴的变形 key。
 *
 * 注意别用「分类 id」硬拼：服务端下发的网页分区有的干脆没有 `_id`，
 * 拼出来全是同一个 key，几个磁贴会共用一份变形状态，动画就变成一起挤到一处。
 */
internal fun categoryTileKey(category: Category): String {
    val unique = category.id.ifBlank { category.title }
    return "categoryWeb-$unique"
}

/**
 * 广告页域名的兜底值。
 *
 * 原版客户端里写死的是 `http://pica-juicy.picacomic.com`（`KEY_AD_URL_BASE` 的默认值），
 * 但开屏时会用 waka init 下发的值覆盖掉 —— 那个写死的域名现在 404 了，实际在用的是这个。
 */
internal const val FALLBACK_ADS_BASE = "https://pica-ad-api.diwodiwo.xyz"

/** 「支持嗶咔」打开的地址：`KEY_AD_URL_BASE + /android/font`，深色主题带 `?color=black`。 */
internal fun supportPicaUrl(adsBase: String?, dark: Boolean): String {
    val base = adsBase?.trimEnd('/')?.takeIf { it.startsWith("http") } ?: FALLBACK_ADS_BASE
    return "$base/android/font" + if (dark) "?color=black" else ""
}

@Stable
class CategoriesState {
    var categories by mutableStateOf<List<Category>?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** 「點擊救嗶咔」磁贴的广告封面。 */
    var adCover by mutableStateOf<String?>(null)
        private set
    /** 「支持嗶咔」内嵌页的域名：官方开屏时从 waka init 拿到，这里跟着广告封面一起问一次。 */
    var adsBase by mutableStateOf<String?>(null)
        private set
    private var started = false

    /** 首屏和下拉刷新走同一段拉取逻辑。 */
    suspend fun load() {
        categories = null
        error = null
        runCatching { pica.meta.categories() }
            .onSuccess { categories = it }
            .onFailure { error = it.message ?: "分类没拉到" }
    }

    /** 只在第一次进来时拉，来回切页面 / 开关整页都不重拉，免得退回来时先空一下。 */
    suspend fun ensureLoaded() {
        if (started) return
        started = true
        load()
    }

    private var adStarted = false

    suspend fun ensureAdCover() {
        if (adStarted) return
        adStarted = true
        adCover = runCatching { pica.meta.banners().firstOrNull()?.thumb.url() }.getOrNull()
        // 广告域名跟封面一起问：开「支持嗶咔」时就不用现等。
        adsBase = runCatching { pica.meta.adsBaseUrl() }.getOrNull()
    }
}

@Composable
fun rememberCategoriesState(): CategoriesState = remember { CategoriesState() }

@Composable
fun CategoriesContent(
    listState: LazyListState = rememberLazyListState(),
    state: CategoriesState = rememberCategoriesState(),
    onOpenForum: () -> Unit = {},
    onOpenGames: () -> Unit = {},
    onOpenLeaderboard: () -> Unit = {},
    forumTileModifier: Modifier = Modifier,
    leaderboardTileModifier: Modifier = Modifier,
    supportTileModifier: Modifier = Modifier,
    latestTileModifier: Modifier = Modifier,
    randomTileModifier: Modifier = Modifier,
    // 分类磁贴：外层给每块挂变形源（不管是开浏览器还是开本子列表），
    // 源和目标都用 [categoryTileKey] 算出来的 key。
    categoryTileModifier: @Composable (Category) -> Modifier = { Modifier },
    onOpenWeb: (Category) -> Unit = {},
    onOpenCategory: (Category) -> Unit = {},
    /** 最新更新：官方是同一个列表 Fragment，只是不带分类、固定 s=dd。 */
    onOpenLatest: () -> Unit = {},
    /** 隨機本子：走 `GET comics/random`，一次一批，无分页。 */
    onOpenRandom: () -> Unit = {},
    /** 支持嗶咔：原版是整屏内嵌网页（`/android/font`），这里走同一个内置浏览器。 */
    onOpenSupport: () -> Unit = {},
    /** 點擊救嗶咔 / 嗶咔小程式：弹嗶咔的開屏廣告。 */
    onOpenSplashAd: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    // 数据放在外层（[CategoriesState]），整页开开关关、栏与栏之间来回切都不重拉。
    LaunchedEffect(state) { state.ensureLoaded() }

    // 官方的「點擊救嗶咔」磁贴是轮播的广告图，这里取第一批广告的封面。
    LaunchedEffect(state) { state.ensureAdCover() }

    PageScaffold(
        state = listState,
        header = { PageHeader(title = "分类") },
        onRefresh = { state.load() },
    ) {
        item {
            SectionTitle("熱門分類")
            Spacer(Modifier.height(12.dp))
            val hotTiles = listOf(
                HotTile(
                    label = "支持嗶咔",
                    art = "support",
                    modifier = supportTileModifier,
                    onClick = onOpenSupport,
                ),
                HotTile(
                    label = "嗶咔排行榜",
                    art = "leaderboard",
                    modifier = leaderboardTileModifier,
                    onClick = onOpenLeaderboard,
                ),
                HotTile("遊戲推薦", art = "game", onClick = onOpenGames),
                HotTile("點擊救嗶咔", url = state.adCover, onClick = onOpenSplashAd),
                HotTile("嗶咔小程式", art = "love_pica", onClick = onOpenSplashAd),
                HotTile("嗶咔留言版", art = "forum", modifier = forumTileModifier, onClick = onOpenForum),
                HotTile(
                    label = "最新更新",
                    art = "latest",
                    modifier = latestTileModifier,
                    onClick = onOpenLatest,
                ),
                HotTile(
                    label = "隨機本子",
                    art = "random",
                    modifier = randomTileModifier,
                    onClick = onOpenRandom,
                ),
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
        val list = state.categories
        when {
            state.error != null -> item { FailedBlock(state.error.orEmpty()) { scope.launch { state.load() } } }
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
                                onClick = {
                                    val link = category.link
                                    if (category.isWeb && !link.isNullOrBlank()) {
                                        onOpenWeb(category)
                                    } else {
                                        onOpenCategory(category)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .then(categoryTileModifier(category)),
                            )
                        }
                        if (rows[index].size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.surface,
        tonalElevation = 1.dp,
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
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val description = category.description?.takeIf { it.isNotBlank() && it != "未知" }
                if (!description.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
