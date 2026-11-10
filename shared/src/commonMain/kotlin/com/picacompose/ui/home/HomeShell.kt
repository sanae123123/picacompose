package com.picacompose.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.picacompose.picaapi.model.UserProfile
import com.picacompose.ui.components.BackHandlerCompat
import com.picacompose.data.NoticeUnread
import com.picacompose.ui.components.PicaBackdrop
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.overlayBounds
import com.picacompose.ui.components.WebPageContent
import com.picacompose.ui.components.overlayPageFade
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 从按钮长出来的整屏页面。 */
private enum class OverlayPage {
    None, Messages, Forum, Leaderboard, Web, Comics, Detail, Random, Reader, Search,
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
        // 详情页的 key 跟着点的那一本走，这里只是兜底。
        OverlayPage.Detail -> "comicDetail"
        OverlayPage.Random -> "randomTile"
        // 阅读器的 key 跟着点的那一话走，这里只是兜底。
        OverlayPage.Reader -> "episode"
        // 搜索是从分类页顶栏那颗按钮打开的，没有对应的源，纯淡入。
        OverlayPage.Search -> "searchPage"
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
    val tabs = HomeTab.entries
    var savedIndex by rememberSaveable { mutableStateOf(0) }
    val pagerState = rememberPagerState(initialPage = savedIndex, pageCount = { tabs.size })
    val tab = tabs[pagerState.currentPage.coerceIn(tabs.indices)]
    // 进程被杀掉再回来还能停在原来那一栏。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { savedIndex = it }
    }
    // 登录时拉到的那份资料会一直用到下次登录，签到状态早就过期了；
    // 每次进「我的」都重拉一次，才不会出现「刚签到过、再进来又显示未签到」。
    LaunchedEffect(tab) {
        if (tab == HomeTab.Mine) onRefreshProfile()
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
    var comicSort by rememberSaveable { mutableStateOf<String?>(null) }
    val comicPager = remember(comicTitle, comicSort) {
        Pager(
            load = { page ->
                pica.comics.list(
                    page = page,
                    category = comicTitle.ifBlank { null },
                    sort = comicSort,
                )
            },
            itemKey = { it.id },
        )
    }
    val comicListState = rememberLazyListState()
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
    LaunchedEffect(tab) {
        when (tab) {
            HomeTab.Home -> homeListState.scrollToItem(0)
            HomeTab.Category -> categoryListState.scrollToItem(0)
            HomeTab.Game -> gameListState.scrollToItem(0)
            HomeTab.Mine, HomeTab.Settings -> Unit
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
            OverlayPage.Search -> if (!fromDetail) searchListState.scrollToItem(0)
            OverlayPage.None, OverlayPage.Leaderboard, OverlayPage.Web,
            OverlayPage.Detail, OverlayPage.Random, OverlayPage.Reader -> Unit
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
                                selected = tab,
                                onSelect = {
                                    overlayOrdinal = OverlayPage.None.ordinal
                                    scope.launch { pagerState.animateScrollToPage(it.ordinal) }
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
                                            comicSort = null
                                            comicKey = categoryTileKey(category)
                                            overlayOrdinal = OverlayPage.Comics.ordinal
                                        },
                                        onOpenLatest = {
                                            comicTitle = ""
                                            comicLabel = "最新更新"
                                            comicSort = "dd"
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
                                    if (fullBleed) Color.Black else MaterialTheme.colorScheme.background,
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
private fun PicaNavigationBar(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    NavigationBar(
        containerColor = colors.surface.copy(alpha = 0.94f),
        tonalElevation = 3.dp,
    ) {
        HomeTab.entries.forEach { tab ->
            val isSelected = tab == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                icon = {
                    PicaGlyph(
                        icon = tab.icon,
                        modifier = Modifier.size(22.dp),
                        tint = if (isSelected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                        filled = isSelected,
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.onSecondaryContainer,
                    selectedTextColor = colors.onSurface,
                    indicatorColor = colors.secondaryContainer,
                    unselectedIconColor = colors.onSurfaceVariant,
                    unselectedTextColor = colors.onSurfaceVariant,
                ),
            )
        }
    }
}
