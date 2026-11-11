package com.picacompose.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.material3.Icon as Md3Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar as Md3NavigationBar
import androidx.compose.material3.NavigationBarItem as Md3NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.picacompose.data.pica
import com.picacompose.picaapi.model.ComicSummary
import com.picacompose.picaapi.model.UserProfile
import com.picacompose.ui.components.BackHandlerCompat
import com.picacompose.data.NoticeUnread
import com.picacompose.data.keepImageIpsFresh
import com.picacompose.data.refreshImageHost
import com.picacompose.ui.components.PicaBackdrop
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.md3Skin
import com.picacompose.ui.components.vector
import com.picacompose.ui.components.overlayBounds
import com.picacompose.ui.components.WebPageContent
import com.picacompose.ui.components.overlayPageFade
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

/** 从按钮长出来的整屏页面。 */
private enum class OverlayPage {
    None, Messages, Forum, Leaderboard, Web, Comics, TagComics, Detail, Random, Reader, Search,
    Favorites, MyComments, ReadHistory, EditProfile,
}

/** 变形动画要配对用的 key：源（按钮/卡片）和目标（整页）必须一致。 */
private val OverlayPage.sharedKey: String
    get() = when (this) {
        OverlayPage.Messages -> "messagesCard"
        OverlayPage.Forum -> "forumTile"
        OverlayPage.Leaderboard -> "leaderboardTile"
        // 网页整页的 key 跟着具体磁贴走，这里只是兜底。
        OverlayPage.Web -> "webPage"
        OverlayPage.Comics -> "comicsPage"
        // 标签结果页复用列表页的变形 key，纯淡入也行。
        OverlayPage.TagComics -> "comicsPage"
        // 详情页的 key 跟着点的那一本走，这里只是兜底。
        OverlayPage.Detail -> "comicDetail"
        OverlayPage.Random -> "randomTile"
        // 阅读器的 key 跟着点的那一话走，这里只是兜底。
        OverlayPage.Reader -> "episode"
        // 搜索是从分类页顶栏那颗按钮打开的，没有对应的源，纯淡入。
        OverlayPage.Search -> "searchPage"
        OverlayPage.Favorites -> "favoritesCard"
        OverlayPage.MyComments -> "myCommentsCard"
        OverlayPage.ReadHistory -> "readHistoryCard"
        // 从「我的」右上角那颗按钮长成整页。
        OverlayPage.EditProfile -> "editProfileButton"
        OverlayPage.None -> "mainShell"
    }

/**
 * 登录之后的壳：底部五个入口 + 对应内容。
 *
 * 页面用一个横向 pager 装：点底部栏滑过去，左右拖动也能翻页，翻页动画由 pager 自己带。
 * 各页自己管滚动和内边距，这里只负责切换和底部栏。
 *
 * 消息页、留言板是「点按钮打开的整页」：用 SharedTransitionLayout 的容器变形，
 * 从卡片 / 磁贴那一块长成整屏（盖住底部栏），返回时缩回原处。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomeShell(
    profile: UserProfile,
    onSignOut: () -> Unit,
    onRefreshProfile: suspend () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val darkTheme = isSystemInDarkTheme()
    // 官方 App 启动时会拉一次 /init，里面带图片服务器地址；拼图片地址时优先用它，
    // 因为官方图床域名在很多网络上会解析到错的 IP。
    LaunchedEffect(Unit) { refreshImageHost() }
    LaunchedEffect(Unit) { keepImageIpsFresh() }
    val tabs = HomeTab.entries
    var savedIndex by rememberSaveable { mutableStateOf(0) }
    val pagerState = rememberPagerState(initialPage = savedIndex, pageCount = { tabs.size })
    // 选中哪一栏只在底部栏里读（见 [PicaNavigationBar]）：这里一旦读 pagerState.currentPage，
    // 每换一次页整个主界面连同两页内容都要重组一遍，横滑的时候就是这么卡的。
    // 进程被杀掉再回来还能停在原来那一栏。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { savedIndex = it }
    }
    // 登录时拉到的那份资料会一直用到下次登录，签到状态早就过期了；
    // 每次进「我的」都重拉一次，才不会出现「刚签到过、再进来又显示未签到」。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                if (tabs[page.coerceIn(tabs.indices)] == HomeTab.Mine) onRefreshProfile()
            }
    }
    // 叠在主页上的两级整页：消息（从首页卡片开）、留言板（从分类磁贴开）。
    var overlayOrdinal by rememberSaveable { mutableStateOf(OverlayPage.None.ordinal) }
    // 分类里 isWeb 的条目：点开哪个就把它的标题 / 地址 / 变形 key 记下来。
    var webKey by rememberSaveable { mutableStateOf("webPage") }
    var webTitle by rememberSaveable { mutableStateOf("") }
    var webUrl by rememberSaveable { mutableStateOf("") }
    // 哔咔的开屏广告：分类页那几块磁贴点开弹一次。
    var splashAdOpen by rememberSaveable { mutableStateOf(false) }
    // 本子列表：查询用的分类名（「最新更新」是空的）、显示用的标题、变形 key，
    // 以及它自己的分页和滚动位置。
    var comicTitle by rememberSaveable { mutableStateOf("") }
    var comicLabel by rememberSaveable { mutableStateOf("") }
    var comicKey by rememberSaveable { mutableStateOf("comicsPage") }
    // 默认按最新排；服务端的原顺序在筛选里还留着（「默认」那一项）。
    var comicSort by rememberSaveable { mutableStateOf<String?>("dd") }
    // 完结筛选是枚举，rememberSaveable 能直接存；换排序 / 换筛选都会换一个 pager，从头拉。
    var comicFinished by rememberSaveable { mutableStateOf(ComicFinished.All) }
    // 标签筛选：多个标签要求同时包含。服务端只认一个 `t`，所以第一个丢给接口，其余在本地筛。
    // rememberSaveable 存 List 不稳，用换行拼成一个字符串。
    var comicTagsRaw by rememberSaveable { mutableStateOf("") }
    val comicTags = remember(comicTagsRaw) {
        comicTagsRaw.split('\n').filter { it.isNotBlank() }
    }
    // 标签黑名单：带这些标签的本子不要。服务端不支持排除，本地筛。
    var comicExcludedTagsRaw by rememberSaveable { mutableStateOf("") }
    val comicExcludedTags = remember(comicExcludedTagsRaw) {
        comicExcludedTagsRaw.split('\n').filter { it.isNotBlank() }
    }
    val comicPager = remember(comicTitle, comicSort, comicFinished, comicTagsRaw, comicExcludedTagsRaw) {
        val extraTags = comicTags.drop(1)
        val excluded = comicExcludedTags
        Pager(
            load = { page ->
                pica.comics.list(
                    page = page,
                    category = comicTitle.ifBlank { null },
                    tag = comicTags.firstOrNull(),
                    sort = comicSort,
                    finished = comicFinished.value,
                )
            },
            itemKey = { it.id },
            filter = if (extraTags.isEmpty() && excluded.isEmpty()) {
                null
            } else {
                { comic: ComicSummary ->
                    extraTags.all { tag -> comic.tags.any { it.equals(tag, ignoreCase = true) } } &&
                        excluded.none { tag -> comic.tags.any { it.equals(tag, ignoreCase = true) } }
                }
            },
        )
    }
    val comicListState = rememberLazyListState()
    // 详情页里点标签打开的「同类本子」页：自己一套筛选条件，不和分类列表互相覆盖。
    var tagPageTagsRaw by rememberSaveable { mutableStateOf("") }
    var tagPageSort by rememberSaveable { mutableStateOf<String?>("dd") }
    var tagPageFinished by rememberSaveable { mutableStateOf(ComicFinished.All) }
    var tagPageExcludedTagsRaw by rememberSaveable { mutableStateOf("") }
    val tagPageTags = remember(tagPageTagsRaw) {
        tagPageTagsRaw.split('\n').filter { it.isNotBlank() }
    }
    val tagPageExcludedTags = remember(tagPageExcludedTagsRaw) {
        tagPageExcludedTagsRaw.split('\n').filter { it.isNotBlank() }
    }
    val tagPagePager = remember(tagPageTagsRaw, tagPageSort, tagPageFinished, tagPageExcludedTagsRaw) {
        val extraTags = tagPageTags.drop(1)
        val excluded = tagPageExcludedTags
        Pager(
            load = { page ->
                pica.comics.list(
                    page = page,
                    tag = tagPageTags.firstOrNull(),
                    sort = tagPageSort,
                    finished = tagPageFinished.value,
                )
            },
            itemKey = { it.id },
            filter = if (extraTags.isEmpty() && excluded.isEmpty()) {
                null
            } else {
                { comic: ComicSummary ->
                    extraTags.all { tag -> comic.tags.any { it.equals(tag, ignoreCase = true) } } &&
                        excluded.none { tag -> comic.tags.any { it.equals(tag, ignoreCase = true) } }
                }
            },
        )
    }
    // 标签结果页从哪来：退出时要分清「还在看原来那本」和「点了别的本子」，不然返回会打转。
    var tagPageOriginComicId by rememberSaveable { mutableStateOf("") }
    var tagPageReturnOrdinal by rememberSaveable { mutableStateOf(OverlayPage.None.ordinal) }
    val tagPageListState = rememberLazyListState()
    // 本子详情：点哪本记它 id / 标题，变形 key 按 id 生成，和列表里那一行对得上。
    var detailComicId by rememberSaveable { mutableStateOf("") }
    var detailTitle by rememberSaveable { mutableStateOf("") }
    // 从哪儿点进详情，退出去就回哪儿（列表页 / 首页 / 排行榜）。
    var detailReturnOrdinal by rememberSaveable { mutableStateOf(OverlayPage.None.ordinal) }
    // 详情页的 key 由开它的那个入口决定（列表行 / 首页封面 / 排行榜行各不相同），
    // 所以这里存下来，不按 id 硬拼。
    var detailKey by rememberSaveable { mutableStateOf("comicDetail") }
    // 详情页的状态提到这里：进阅读器再退回详情时页面会被重建，
    // 数据和滚动位置留在外面，退回来还是刚才那一屏。
    val detailState = remember(detailComicId) {
        if (detailComicId.isBlank()) null else ComicDetailState(detailComicId)
    }
    // 阅读器：从章节那一行撑开成整屏，读的是哪一话也记下来。
    var readerOrder by rememberSaveable { mutableIntStateOf(0) }
    var readerKey by rememberSaveable { mutableStateOf("episode") }
    // 阅读器从第几页开始（上次读到哪儿）。
    var readerPage by rememberSaveable { mutableIntStateOf(0) }
    val overlay = OverlayPage.entries.getOrElse(overlayOrdinal) { OverlayPage.None }
    // 网页视图等整页变形放完再挂（平台视图不参与容器变形）。挂上之后由页面自己淡进来。
    var webMounted by remember { mutableStateOf(false) }
    LaunchedEffect(overlay) {
        if (overlay == OverlayPage.Web) {
            delay(360)
            webMounted = true
        } else {
            // 返回时先卸掉，缩回磁贴的那段也交给页面本身。
            webMounted = false
        }
    }
    // 消息是主页里的一层，没有独立的底部入口；退出去回到首页。
    val messagePager = remember {
        Pager(
            load = { page -> pica.users.notifications(page = page) },
            itemKey = { it.id },
        )
    }
    val messageListState = rememberLazyListState()
    // 我的页那三张入口卡打开的整页：分页和滚动位置都提上来，退出去再进来不用重拉。
    val favouritePager = remember {
        Pager(
            load = { page -> pica.comics.favourites(page = page) },
            itemKey = { it.id },
        )
    }
    val favouriteListState = rememberLazyListState()
    val myCommentPager = remember {
        Pager(
            load = { page -> pica.comments.myComments(page = page) },
            itemKey = { it.id },
        )
    }
    val myCommentListState = rememberLazyListState()
    val historyListState = rememberLazyListState()
    // 留言板挂在分类页里，同样提上来：退出去再进来不重拉第一页。
    val forumFeed = rememberCommentFeed(FORUM_COMIC_ID)
    val forumListState = rememberLazyListState()
    // 首页那条流也提上来：打开消息页再退回来时，不重拉推荐、不跳回顶部。
    val homeListState = rememberLazyListState()
    val homeFeed = rememberHomeFeedState()
    // 首页「消息」卡的小红点：接口没有已读字段，靠最新一条通知的 id 比对。
    val noticeUnread = remember { NoticeUnread() }
    LaunchedEffect(overlay) {
        // 从消息页退回来时再看一眼，有新消息照样亮。
        if (overlay == OverlayPage.None) noticeUnread.refresh()
    }
    // 放这里让页面切走再回来时保留已加载的分页和滚动位置。
    val gamePager = remember {
        Pager(
            load = { page -> pica.games.list(page = page) },
            itemKey = { it.id },
        )
    }
    val gameListState = rememberLazyStaggeredGridState()
    // 分类页的列表位置也提上来，换页时好把它拨回顶部。
    val categoryListState = rememberLazyListState()
    // 分类数据同样提上来：开关整页时页面会被重建，退回来不该重新拉一遍。
    val categoriesState = rememberCategoriesState()
    // 搜索：输入框、搜过的词和结果都留在这里，翻完详情退回来还是那一批。
    val searchState = rememberSearchState()
    val searchListState = rememberLazyListState()

    // 换了一页就从头看：底部栏切到哪一栏，那一栏的列表滚回顶部。
    // 等这一栏停稳再拨，不然横滑动画正播着往列表里插一堆组合，一帧就爆了。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                when (tabs[page.coerceIn(tabs.indices)]) {
                    HomeTab.Home -> homeListState.scrollToItem(0)
                    HomeTab.Category -> categoryListState.scrollToItem(0)
                    HomeTab.Game -> gameListState.scrollToItem(0)
                    HomeTab.Mine, HomeTab.Settings -> Unit
                }
            }
    }

    // 打开整页也从头看，别接着上次读到的地方。
    // 但从详情页退回列表页不算「打开整页」：刚翻到第几十本，退回来当然还在那一页。
    var lastOverlay by remember { mutableStateOf(OverlayPage.None) }
    LaunchedEffect(overlay) {
        val fromDetail = lastOverlay == OverlayPage.Detail
        lastOverlay = overlay
        when (overlay) {
            OverlayPage.Messages -> messageListState.scrollToItem(0)
            OverlayPage.Forum -> forumListState.scrollToItem(0)
            OverlayPage.Comics -> if (!fromDetail) comicListState.scrollToItem(0)
            OverlayPage.TagComics -> if (!fromDetail) tagPageListState.scrollToItem(0)
            OverlayPage.Search -> if (!fromDetail) searchListState.scrollToItem(0)
            // 收藏和历史评论是会被别处改动的内容（收藏了新的、发了新评论），每次打开重拉第一页；
            // 从详情页退回来不算打开，保持刚才滚到的地方。
            OverlayPage.Favorites -> if (!fromDetail) {
                favouriteListState.scrollToItem(0)
                favouritePager.reloadFirstPage()
            }

            OverlayPage.MyComments -> if (!fromDetail) {
                myCommentListState.scrollToItem(0)
                myCommentPager.reloadFirstPage()
            }

            OverlayPage.ReadHistory -> if (!fromDetail) historyListState.scrollToItem(0)
            OverlayPage.None, OverlayPage.Leaderboard, OverlayPage.Web,
            OverlayPage.Detail, OverlayPage.Random, OverlayPage.Reader,
            OverlayPage.EditProfile -> Unit
        }
    }

    // 各栏的滚动位置在滑走再回来时也能接上（懒列表的状态本身是可保存的）。
    val tabStates = rememberSaveableStateHolder()

    // 退出搜索就把搜索页清干净：下次从分类点进来是空的，不再挂着上次的词和结果。
    val exitSearch: () -> Unit = {
        searchState.reset()
        overlayOrdinal = OverlayPage.None.ordinal
    }

    // 整页开着的时候，系统返回先退回上一屏，而不是直接退出 App。
    BackHandlerCompat(enabled = overlay != OverlayPage.None) {
        // 阅读器退回详情页；详情页退回它被点开的地方（列表页 / 排行榜）；
        // 搜索退回分类页（顺手清空搜索页）；别的整页直接回主界面。
        when (overlay) {
            OverlayPage.Detail -> overlayOrdinal = detailReturnOrdinal
            OverlayPage.TagComics -> overlayOrdinal = if (detailComicId == tagPageOriginComicId) {
                OverlayPage.Detail.ordinal
            } else {
                tagPageReturnOrdinal
            }
            OverlayPage.Reader -> overlayOrdinal = OverlayPage.Detail.ordinal
            OverlayPage.Search -> exitSearch()
            else -> overlayOrdinal = OverlayPage.None.ordinal
        }
    }

    Box(Modifier.fillMaxSize()) {
        PicaBackdrop()
        SharedTransitionLayout {
            val shared = this
            AnimatedContent(
                targetState = overlay,
                transitionSpec = { overlayPageFade() },
                modifier = Modifier.fillMaxSize(),
                label = "overlayPage",
            ) { page ->
                val contentScope = this
                if (page == OverlayPage.None) {
                    // 主界面：消息卡和留言板磁贴各自挂上变形源，等被点的时候长成整页。
                    val messagesSource = with(shared) {
                        overlayBounds(OverlayPage.Messages.sharedKey, contentScope)
                    }
                    val forumSource = with(shared) {
                        overlayBounds(OverlayPage.Forum.sharedKey, contentScope)
                    }
                    val leaderboardSource = with(shared) {
                        overlayBounds(OverlayPage.Leaderboard.sharedKey, contentScope)
                    }
                    Scaffold(
                        containerColor = Color.Transparent,
                        bottomBar = {
                            PicaNavigationBar(
                                pagerState = pagerState,
                                onSelect = { target ->
                                    overlayOrdinal = OverlayPage.None.ordinal
                                    scope.launch {
                                        // 隔好几栏照样滑过去，只是把时长压短：中途那几栏还是会被组合一遍，
                                        // 动画越短越少卡；相邻两栏用默认的那条。
                                        if (abs(target.ordinal - pagerState.currentPage) > 1) {
                                            pagerState.animateScrollToPage(
                                                page = target.ordinal,
                                                animationSpec = tween(
                                                    durationMillis = 260,
                                                    easing = FastOutSlowInEasing,
                                                ),
                                            )
                                        } else {
                                            pagerState.animateScrollToPage(target.ordinal)
                                        }
                                    }
                                },
                            )
                        },
                    ) { inner ->
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize().padding(inner),
                            // 整页盖上来的时候别让横滑把底下的栏翻走。
                            userScrollEnabled = true,
                            key = { tabs[it].name },
                        ) { index ->
                            tabStates.SaveableStateProvider(tabs[index].name) {
                                when (tabs[index]) {
                                    HomeTab.Home -> HomeContent(
                                        profile = profile,
                                        listState = homeListState,
                                        feed = homeFeed,
                                        messagesUnread = noticeUnread.hasUnread,
                                        messagesCardModifier = messagesSource,
                                        onOpenMessages = {
                                            overlayOrdinal = OverlayPage.Messages.ordinal
                                        },
                                        // 每张封面挂一个变形源，点开时从它长成整屏。
                                        comicCoverModifier = { key ->
                                            with(shared) { overlayBounds(key, contentScope) }
                                        },
                                        onOpenComic = { comic, key ->
                                            detailComicId = comic.id
                                            detailTitle = comic.title
                                            detailKey = key
                                            detailReturnOrdinal = OverlayPage.None.ordinal
                                            overlayOrdinal = OverlayPage.Detail.ordinal
                                        },
                                    )

                                    HomeTab.Category -> CategoriesContent(
                                        listState = categoryListState,
                                        state = categoriesState,
                                        forumTileModifier = forumSource,
                                        leaderboardTileModifier = leaderboardSource,
                                        supportTileModifier = with(shared) {
                                            overlayBounds("supportTile", contentScope)
                                        },
                                        latestTileModifier = with(shared) {
                                            overlayBounds("latestTile", contentScope)
                                        },
                                        randomTileModifier = with(shared) {
                                            overlayBounds(OverlayPage.Random.sharedKey, contentScope)
                                        },
                                        // isWeb 的分类磁贴各自挂一个变形源，点开时从它身上长成整屏。
                                        categoryTileModifier = { category ->
                                            with(shared) {
                                                overlayBounds(categoryTileKey(category), contentScope)
                                            }
                                        },
                                        onOpenWeb = { category ->
                                            webKey = categoryTileKey(category)
                                            webTitle = category.title
                                            webUrl = category.link.orEmpty()
                                            overlayOrdinal = OverlayPage.Web.ordinal
                                        },
                                        onOpenCategory = { category ->
                                            comicTitle = category.title
                                            comicLabel = category.title
                                            comicSort = "dd"
                                            comicFinished = ComicFinished.All
                                            comicTagsRaw = ""
                                            comicExcludedTagsRaw = ""
                                            comicKey = categoryTileKey(category)
                                            overlayOrdinal = OverlayPage.Comics.ordinal
                                        },
                                        onOpenLatest = {
                                            comicTitle = ""
                                            comicLabel = "最新更新"
                                            comicSort = "dd"
                                            comicFinished = ComicFinished.All
                                            comicTagsRaw = ""
                                            comicExcludedTagsRaw = ""
                                            comicKey = "latestTile"
                                            overlayOrdinal = OverlayPage.Comics.ordinal
                                        },
                                        onOpenRandom = {
                                            overlayOrdinal = OverlayPage.Random.ordinal
                                        },
                                        onOpenLeaderboard = {
                                            overlayOrdinal = OverlayPage.Leaderboard.ordinal
                                        },
                                        onOpenForum = {
                                            overlayOrdinal = OverlayPage.Forum.ordinal
                                        },
                                        // 「支持哔咔」也走统一的内置浏览器，key 用它自己那块磁贴，
                                        // 点下去从磁贴长成整屏，不再另开一层弹窗。
                                        onOpenSupport = {
                                            webKey = "supportTile"
                                            webTitle = "支持哔咔"
                                            webUrl = supportPicaUrl(
                                                adsBase = categoriesState.adsBase,
                                                dark = darkTheme,
                                            )
                                            overlayOrdinal = OverlayPage.Web.ordinal
                                        },
                                        onOpenSplashAd = { splashAdOpen = true },
                                        onOpenSearch = {
                                            searchState.requestFocus()
                                            overlayOrdinal = OverlayPage.Search.ordinal
                                        },
                                        onOpenGames = {
                                            overlayOrdinal = OverlayPage.None.ordinal
                                            scope.launch {
                                                pagerState.animateScrollToPage(HomeTab.Game.ordinal)
                                            }
                                        },
                                    )

                                    HomeTab.Game -> GamesContent(pager = gamePager, listState = gameListState)
                                    HomeTab.Mine -> MineContent(
                                        profile = profile,
                                        onSignOut = onSignOut,
                                        onRefreshProfile = onRefreshProfile,
                                        onOpenFavourites = {
                                            overlayOrdinal = OverlayPage.Favorites.ordinal
                                        },
                                        onOpenComments = {
                                            overlayOrdinal = OverlayPage.MyComments.ordinal
                                        },
                                        onOpenHistory = {
                                            overlayOrdinal = OverlayPage.ReadHistory.ordinal
                                        },
                                        onOpenEditProfile = {
                                            overlayOrdinal = OverlayPage.EditProfile.ordinal
                                        },
                                        // 三张卡各挂一个变形源，点开就从那一块长成整屏。
                                        favouritesModifier = with(shared) {
                                            overlayBounds(OverlayPage.Favorites.sharedKey, contentScope)
                                        },
                                        commentsModifier = with(shared) {
                                            overlayBounds(OverlayPage.MyComments.sharedKey, contentScope)
                                        },
                                        historyModifier = with(shared) {
                                            overlayBounds(OverlayPage.ReadHistory.sharedKey, contentScope)
                                        },
                                        editProfileModifier = with(shared) {
                                            overlayBounds(OverlayPage.EditProfile.sharedKey, contentScope)
                                        },
                                    )
                                    HomeTab.Settings -> SettingsContent(onSignOut = onSignOut)
                                }
                            }
                        }
                    }
                } else {
                    // 整页本体：从按钮那一块变形到整屏，所以它自带底色、盖住底部栏。
                    val pageKey = when (page) {
                        OverlayPage.Web -> webKey
                        OverlayPage.Comics -> comicKey
                        OverlayPage.Detail -> detailKey
                        OverlayPage.Reader -> readerKey
                        else -> page.sharedKey
                    }
                    val pageBounds = with(shared) {
                        overlayBounds(pageKey, contentScope)
                    }
                    // 阅读器是整屏黑底、铺到系统栏下面的，别的整页才是常规底色。
                    val fullBleed = page == OverlayPage.Reader
                    Box(modifier = pageBounds) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    if (fullBleed) Color.Black else MiuixTheme.colorScheme.background,
                                ),
                        )
                        if (!fullBleed) PicaBackdrop()
                        Box(
                            Modifier
                                .fillMaxSize()
                                .then(
                                    if (fullBleed) {
                                        Modifier
                                    } else {
                                        Modifier.windowInsetsPadding(WindowInsets.systemBars)
                                    },
                                ),
                        ) {
                            when (page) {
                                OverlayPage.Messages -> MessagesContent(
                                    pager = messagePager,
                                    listState = messageListState,
                                    onBack = { overlayOrdinal = OverlayPage.None.ordinal },
                                    onNewestLoaded = noticeUnread::markSeen,
                                )

                                OverlayPage.Forum -> ForumContent(
                                    pager = forumFeed.pager,
                                    listState = forumListState,
                                    onBack = { overlayOrdinal = OverlayPage.None.ordinal },
                                    topComments = forumFeed.topComments,
                                )

                                OverlayPage.Leaderboard -> LeaderboardContent(
                                    onBack = { overlayOrdinal = OverlayPage.None.ordinal },
                                    comicRowModifier = { key ->
                                        with(shared) { overlayBounds(key, contentScope) }
                                    },
                                    onOpenComic = { comic, key ->
                                        detailComicId = comic.id
                                        detailTitle = comic.title
                                        detailKey = key
                                        detailReturnOrdinal = OverlayPage.Leaderboard.ordinal
                                        overlayOrdinal = OverlayPage.Detail.ordinal
                                    },
                                )

                                OverlayPage.Web -> WebPageContent(
                                    title = webTitle,
                                    url = webUrl,
                                    showWebView = webMounted,
                                    onBack = { overlayOrdinal = OverlayPage.None.ordinal },
                                )

                                OverlayPage.Comics -> ComicListContent(
                                    title = comicLabel,
                                    pager = comicPager,
                                    listState = comicListState,
                                    sort = comicSort,
                                    onSortChange = { comicSort = it },
                                    finished = comicFinished,
                                    onFinishedChange = { comicFinished = it },
                                    tags = comicTags,
                                    onTagsChange = { comicTagsRaw = it.joinToString("\n") },
                                    excludedTags = comicExcludedTags,
                                    onExcludedTagsChange = { comicExcludedTagsRaw = it.joinToString("\n") },
                                    // 每一行各自挂一个变形源，点开就从那一行长成整屏。
                                    comicRowModifier = { key ->
                                        with(shared) { overlayBounds(key, contentScope) }
                                    },
                                    onOpenComic = { comic, key ->
                                        detailComicId = comic.id
                                        detailTitle = comic.title
                                        detailKey = key
                                        detailReturnOrdinal = OverlayPage.Comics.ordinal
                                        overlayOrdinal = OverlayPage.Detail.ordinal
                                    },
                                    onBack = { overlayOrdinal = OverlayPage.None.ordinal },
                                )

                                OverlayPage.TagComics -> ComicListContent(
                                    title = "标签：${tagPageTags.firstOrNull().orEmpty()}",
                                    pager = tagPagePager,
                                    listState = tagPageListState,
                                    sort = tagPageSort,
                                    onSortChange = { tagPageSort = it },
                                    finished = tagPageFinished,
                                    onFinishedChange = { tagPageFinished = it },
                                    tags = tagPageTags,
                                    onTagsChange = { tagPageTagsRaw = it.joinToString("\n") },
                                    excludedTags = tagPageExcludedTags,
                                    onExcludedTagsChange = { tagPageExcludedTagsRaw = it.joinToString("\n") },
                                    comicRowModifier = { key ->
                                        with(shared) { overlayBounds(key, contentScope) }
                                    },
                                    onOpenComic = { comic, key ->
                                        detailComicId = comic.id
                                        detailTitle = comic.title
                                        detailKey = key
                                        detailReturnOrdinal = OverlayPage.TagComics.ordinal
                                        overlayOrdinal = OverlayPage.Detail.ordinal
                                    },
                                    // 还在看原来那本就回详情；在标签页里点过别的本子，就回进详情前的页面。
                                    onBack = {
                                        overlayOrdinal = if (detailComicId == tagPageOriginComicId) {
                                            OverlayPage.Detail.ordinal
                                        } else {
                                            tagPageReturnOrdinal
                                        }
                                    },
                                )

                                OverlayPage.Search -> SearchContent(
                                    state = searchState,
                                    listState = searchListState,
                                    onBack = exitSearch,
                                    comicRowModifier = { key ->
                                        with(shared) { overlayBounds(key, contentScope) }
                                    },
                                    onOpenComic = { comic, key ->
                                        detailComicId = comic.id
                                        detailTitle = comic.title
                                        detailKey = key
                                        detailReturnOrdinal = OverlayPage.Search.ordinal
                                        overlayOrdinal = OverlayPage.Detail.ordinal
                                    },
                                )

                                OverlayPage.Favorites -> FavouritesContent(
                                    pager = favouritePager,
                                    listState = favouriteListState,
                                    onBack = { overlayOrdinal = OverlayPage.None.ordinal },
                                    comicRowModifier = { key ->
                                        with(shared) { overlayBounds(key, contentScope) }
                                    },
                                    onOpenComic = { comic, key ->
                                        detailComicId = comic.id
                                        detailTitle = comic.title
                                        detailKey = key
                                        detailReturnOrdinal = OverlayPage.Favorites.ordinal
                                        overlayOrdinal = OverlayPage.Detail.ordinal
                                    },
                                )

                                OverlayPage.EditProfile -> EditProfileContent(
                                    profile = profile,
                                    onBack = { overlayOrdinal = OverlayPage.None.ordinal },
                                    onSaved = {
                                        onRefreshProfile()
                                        overlayOrdinal = OverlayPage.None.ordinal
                                    },
                                )

                                OverlayPage.MyComments -> MyCommentsContent(
                                    pager = myCommentPager,
                                    listState = myCommentListState,
                                    selfName = profile.name,
                                    onBack = { overlayOrdinal = OverlayPage.None.ordinal },
                                    commentRowModifier = { key ->
                                        with(shared) { overlayBounds(key, contentScope) }
                                    },
                                    onOpenComic = { comicId, title, key ->
                                        detailComicId = comicId
                                        detailTitle = title
                                        detailKey = key
                                        detailReturnOrdinal = OverlayPage.MyComments.ordinal
                                        overlayOrdinal = OverlayPage.Detail.ordinal
                                    },
                                )

                                OverlayPage.ReadHistory -> ReadHistoryContent(
                                    listState = historyListState,
                                    onBack = { overlayOrdinal = OverlayPage.None.ordinal },
                                    comicRowModifier = { key ->
                                        with(shared) { overlayBounds(key, contentScope) }
                                    },
                                    onOpenComic = { comicId, title, key ->
                                        detailComicId = comicId
                                        detailTitle = title
                                        detailKey = key
                                        detailReturnOrdinal = OverlayPage.ReadHistory.ordinal
                                        overlayOrdinal = OverlayPage.Detail.ordinal
                                    },
                                )

                                OverlayPage.Random -> RandomComicsContent(
                                    onBack = { overlayOrdinal = OverlayPage.None.ordinal },
                                    comicRowModifier = { key ->
                                        with(shared) { overlayBounds(key, contentScope) }
                                    },
                                    onOpenComic = { comic, key ->
                                        detailComicId = comic.id
                                        detailTitle = comic.title
                                        detailKey = key
                                        detailReturnOrdinal = OverlayPage.Random.ordinal
                                        overlayOrdinal = OverlayPage.Detail.ordinal
                                    },
                                )

                                OverlayPage.Detail -> detailState?.let { state ->
                                    ComicDetailContent(
                                        state = state,
                                        seedTitle = detailTitle,
                                        onBack = { overlayOrdinal = detailReturnOrdinal },
                                        // 章节行也挂变形源：点开从那一行撑成整屏阅读器。
                                        episodeRowModifier = { key ->
                                            with(shared) { overlayBounds(key, contentScope) }
                                        },
                                        onOpenEpisode = { episode, key ->
                                            readerOrder = episode.order
                                            // 点的正是上次读的那一话就接着看，别从头再来。
                                            val saved = state.progress
                                            readerPage = if (saved != null && saved.order == episode.order) {
                                                saved.page
                                            } else {
                                                0
                                            }
                                            readerKey = key
                                            overlayOrdinal = OverlayPage.Reader.ordinal
                                        },
                                        // 点本子标签：开一个只筛这个标签的本子列表，返回回详情。
                                        onSearchTag = { tag ->
                                            val clean = tag.trim()
                                            if (clean.isNotBlank()) {
                                                tagPageTagsRaw = clean
                                                tagPageExcludedTagsRaw = ""
                                                tagPageSort = "dd"
                                                tagPageFinished = ComicFinished.All
                                                tagPageOriginComicId = detailComicId
                                                tagPageReturnOrdinal = detailReturnOrdinal
                                                overlayOrdinal = OverlayPage.TagComics.ordinal
                                            }
                                        },
                                        // 「继续阅读」：没有对应的章节行，直接用兜底的 key，纯淡入。
                                        onResume = { order, page ->
                                            readerOrder = order
                                            readerPage = page
                                            readerKey = "episode"
                                            overlayOrdinal = OverlayPage.Reader.ordinal
                                        },
                                    )
                                }

                                OverlayPage.Reader -> detailState?.let { state ->
                                    ReaderContent(
                                        comicId = state.comicId,
                                        comicTitle = state.detail?.title
                                            ?: detailTitle,
                                        episodes = state.episodePager.items,
                                        startOrder = readerOrder,
                                        startPage = readerPage,
                                        onDismiss = {
                                            overlayOrdinal = OverlayPage.Detail.ordinal
                                        },
                                        // 读到哪一页就记到本子上，详情页那张「继续阅读」跟着变。
                                        onProgress = { order, page, pages ->
                                            state.recordProgress(order, page, pages)
                                        },
                                    )
                                }

                                OverlayPage.None -> Unit
                            }
                        }
                    }
                }
            }
        }
        if (splashAdOpen) {
            SplashAdDialog(
                onDismiss = { splashAdOpen = false },
                onOpenLink = { url, title ->
                    splashAdOpen = false
                    webKey = "webPage"
                    webTitle = title
                    webUrl = url
                    overlayOrdinal = OverlayPage.Web.ordinal
                },
            )
        }
    }
}

@Composable
private fun PicaNavigationBar(pagerState: PagerState, onSelect: (HomeTab) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val tabs = HomeTab.entries
    // 选中态只在这一层读：换页时整页不重组，只有底部栏这颗会跟着更新。
    val selected by remember(pagerState) {
        derivedStateOf { tabs[pagerState.currentPage.coerceIn(tabs.indices)] }
    }
    if (md3Skin) {
        val md3 = MaterialTheme.colorScheme
        Md3NavigationBar(containerColor = md3.surfaceContainer) {
            tabs.forEach { tab ->
                val active = tab == selected
                Md3NavigationBarItem(
                    selected = active,
                    onClick = { onSelect(tab) },
                    icon = {
                        Md3Icon(
                            imageVector = tab.icon.vector(active),
                            contentDescription = tab.label,
                            tint = if (active) md3.onSecondaryContainer else md3.onSurfaceVariant,
                        )
                    },
                    label = {
                        Text(
                            text = tab.label,
                            style = MiuixTheme.textStyles.footnote2,
                            color = if (active) md3.onSurface else md3.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    } else {
        NavigationBar(
            color = colors.surfaceVariant.copy(alpha = 0.96f),
        ) {
            tabs.forEach { tab ->
                NavigationBarItem(
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    icon = tab.icon.vector(tab == selected),
                    label = tab.label,
                )
            }
        }
    }
}
