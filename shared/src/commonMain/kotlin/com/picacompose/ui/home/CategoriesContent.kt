package com.picacompose.ui.home

import com.picacompose.ui.components.PicaSurface
import com.picacompose.ui.components.PicaIconButton
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.size
import top.yukonga.miuix.kmp.basic.IconButton
import com.picacompose.ui.theme.SkinTokens
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.picacompose.data.pica
import com.picacompose.picaapi.model.Category
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.PicaImage
import com.picacompose.ui.components.url

/**
 * 分类页：照官方版式来 —— 上面一块「热门分类」写死磁贴，下面「全部分类」走接口。
 *
 * 磁贴图和顺序都对齐官方 App：顺序是 支持哔咔 / 排行榜 / 游戏推荐 / 点击救哔咔 /
 * 小程序 / 留言板 / 最新更新 / 随机本子；除「点击救哔咔」用广告图外，其余是官方资源包
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

/** 「支持哔咔」打开的地址：`KEY_AD_URL_BASE + /android/font`，深色主题带 `?color=black`。 */
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
    /** 「点击救哔咔」磁贴的广告封面。 */
    var adCover by mutableStateOf<String?>(null)
        private set
    /** 「支持哔咔」内嵌页的域名：官方开屏时从 waka init 拿到，这里跟着广告封面一起问一次。 */
    var adsBase by mutableStateOf<String?>(null)
        private set
    private var started = false

    /** 首屏和下拉刷新走同一段拉取逻辑。 */
    suspend fun load() {
        categories = null
        error = null
        catchingLoad { pica.meta.categories() }
            .onSuccess { categories = it }
            .onFailure { error = it.message ?: "分类没拉到" }
    }

    /** 只在第一次进来时拉，来回切页面 / 开关整页都不重拉，免得退回来时先空一下。 */
    suspend fun ensureLoaded() {
        if (started) return
        started = true
        try {
            load()
        } catch (e: CancellationException) {
            // 页面被销毁中途取消：不算拉过，下次进来还能再拉。
            started = false
            throw e
        }
    }

    private var adStarted = false

    suspend fun ensureAdCover() {
        if (adStarted) return
        adStarted = true
        try {
            loadAdCover()
        } catch (e: CancellationException) {
            adStarted = false
            throw e
        }
    }

    private suspend fun loadAdCover() {
        adCover = catchingLoad { pica.meta.banners().firstOrNull()?.thumb.url() }.getOrNull()
        // 广告域名跟封面一起问：开「支持哔咔」时就不用现等。
        adsBase = catchingLoad { pica.meta.adsBaseUrl() }.getOrNull()
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
    /** 随机本子：走 `GET comics/random`，一次一批，无分页。 */
    onOpenRandom: () -> Unit = {},
    /** 支持哔咔：原版是整屏内嵌网页（`/android/font`），这里走同一个内置浏览器。 */
    onOpenSupport: () -> Unit = {},
    /** 点击救哔咔 / 哔咔小程序：弹哔咔的开屏广告。 */
    onOpenSplashAd: () -> Unit = {},
    /** 顶栏右上角的搜索：打开搜索页搜本子。 */
    onOpenSearch: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    // 数据放在外层（[CategoriesState]），整页开开关关、栏与栏之间来回切都不重拉。
    LaunchedEffect(state) { state.ensureLoaded() }

    // 官方的「点击救哔咔」磁贴是轮播的广告图，这里取第一批广告的封面。
    LaunchedEffect(state) { state.ensureAdCover() }

    PageScaffold(
        state = listState,
        header = {
            PageHeader(title = "分类") {
                PicaIconButton(onClick = onOpenSearch, modifier = Modifier.size(40.dp)) {
                    PicaGlyph(
                        icon = PicaIcon.Search,
                        modifier = Modifier.size(22.dp),
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                }
            }
        },
        onRefresh = { state.load() },
    ) {
        item {
            SectionTitle("热门分类")
            Spacer(Modifier.height(12.dp))
            val hotTiles = listOf(
                HotTile(
                    label = "支持哔咔",
                    art = "support",
                    modifier = supportTileModifier,
                    onClick = onOpenSupport,
                ),
                HotTile(
                    label = "哔咔排行榜",
                    art = "leaderboard",
                    modifier = leaderboardTileModifier,
                    onClick = onOpenLeaderboard,
                ),
                HotTile("游戏推荐", art = "game", onClick = onOpenGames),
                HotTile("点击救哔咔", url = state.adCover, onClick = onOpenSplashAd),
                HotTile("哔咔小程序", art = "love_pica", onClick = onOpenSplashAd),
                HotTile("哔咔留言板", art = "forum", modifier = forumTileModifier, onClick = onOpenForum),
                HotTile(
                    label = "最新更新",
                    art = "latest",
                    modifier = latestTileModifier,
                    onClick = onOpenLatest,
                ),
                HotTile(
                    label = "随机本子",
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
            SectionTitle("全部分类")
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
        style = MiuixTheme.textStyles.title4,
        color = MiuixTheme.colorScheme.onBackground,
    )
}

/** 「热门分类」里的一块磁贴，图是官方的方形图，标题在下面。 */
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
    val colors = MiuixTheme.colorScheme
    PicaSurface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
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
                style = MiuixTheme.textStyles.footnote1,
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
    val colors = MiuixTheme.colorScheme
    PicaSurface(
        shape = RoundedCornerShape(SkinTokens.rowRadius),
        color = colors.surface,
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
                    style = MiuixTheme.textStyles.subtitle,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val description = category.description?.takeIf { it.isNotBlank() && it != "未知" }
                if (!description.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = description,
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
