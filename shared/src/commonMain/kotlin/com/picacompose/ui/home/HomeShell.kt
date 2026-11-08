package com.picacompose.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
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
import kotlinx.coroutines.launch

/** 从按钮长出来的整屏页面。 */
private enum class OverlayPage { None, Messages, Forum, Leaderboard, Web }

/** 变形动画要配对用的 key：源（按钮/卡片）和目标（整页）必须一致。 */
private val OverlayPage.sharedKey: String
    get() = when (this) {
        OverlayPage.Messages -> "messagesCard"
        OverlayPage.Forum -> "forumTile"
        OverlayPage.Leaderboard -> "leaderboardTile"
        // 网页整页的 key 跟着具体磁贴走，这里只是兜底。
        OverlayPage.Web -> "webPage"
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
fun HomeShell(profile: UserProfile, onSignOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    val tabs = HomeTab.entries
    var savedIndex by rememberSaveable { mutableStateOf(0) }
    val pagerState = rememberPagerState(initialPage = savedIndex, pageCount = { tabs.size })
    val tab = tabs[pagerState.currentPage.coerceIn(tabs.indices)]
    // 进程被杀掉再回来还能停在原来那一栏。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { savedIndex = it }
    }
    // 叠在主页上的两级整页：消息（从首页卡片开）、留言板（从分类磁贴开）。
    var overlayOrdinal by rememberSaveable { mutableStateOf(OverlayPage.None.ordinal) }
    // 分类里 isWeb 的条目：点开哪个就把它的标题 / 地址 / 变形 key 记下来。
    var webKey by rememberSaveable { mutableStateOf("webPage") }
    var webTitle by rememberSaveable { mutableStateOf("") }
    var webUrl by rememberSaveable { mutableStateOf("") }
    val overlay = OverlayPage.entries.getOrElse(overlayOrdinal) { OverlayPage.None }
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
    LaunchedEffect(overlay) {
        when (overlay) {
            OverlayPage.Messages -> messageListState.scrollToItem(0)
            OverlayPage.Forum -> forumListState.scrollToItem(0)
            OverlayPage.None, OverlayPage.Leaderboard, OverlayPage.Web -> Unit
        }
    }

    // 各栏的滚动位置在滑走再回来时也能接上（懒列表的状态本身是可保存的）。
    val tabStates = rememberSaveableStateHolder()

    // 整页开着的时候，系统返回先退回上一屏，而不是直接退出 App。
    BackHandlerCompat(enabled = overlay != OverlayPage.None) {
        overlayOrdinal = OverlayPage.None.ordinal
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
                    // 主界面：消息卡和留言版磁贴各自挂上变形源，等被点的时候长成整页。
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
                                    )

                                    HomeTab.Category -> CategoriesContent(
                                        state = categoryListState,
                                        forumTileModifier = forumSource,
                                        leaderboardTileModifier = leaderboardSource,
                                        // isWeb 的分类磁贴各自挂一个变形源，点开时从它身上长成整屏。
                                        webTileModifier = { id ->
                                            with(shared) {
                                                overlayBounds("categoryWeb-$id", contentScope)
                                            }
                                        },
                                        onOpenWeb = { category ->
                                            webKey = "categoryWeb-${category.id}"
                                            webTitle = category.title
                                            webUrl = category.link.orEmpty()
                                            overlayOrdinal = OverlayPage.Web.ordinal
                                        },
                                        onOpenLeaderboard = {
                                            overlayOrdinal = OverlayPage.Leaderboard.ordinal
                                        },
                                        onOpenForum = {
                                            overlayOrdinal = OverlayPage.Forum.ordinal
                                        },
                                        onOpenGames = {
                                            overlayOrdinal = OverlayPage.None.ordinal
                                            scope.launch {
                                                pagerState.animateScrollToPage(HomeTab.Game.ordinal)
                                            }
                                        },
                                    )

                                    HomeTab.Game -> GamesContent(pager = gamePager, listState = gameListState)
                                    HomeTab.Mine -> MineContent(profile = profile, onSignOut = onSignOut)
                                    HomeTab.Settings -> SettingsContent(onSignOut = onSignOut)
                                }
                            }
                        }
                    }
                } else {
                    // 整页本体：从按钮那一块变形到整屏，所以它自带底色、盖住底部栏。
                    val pageKey = if (page == OverlayPage.Web) webKey else page.sharedKey
                    val pageBounds = with(shared) {
                        overlayBounds(pageKey, contentScope)
                    }
                    Box(modifier = pageBounds) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                        PicaBackdrop()
                        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
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
                                )

                                OverlayPage.Web -> WebPageContent(
                                    title = webTitle,
                                    url = webUrl,
                                    onBack = { overlayOrdinal = OverlayPage.None.ordinal },
                                )

                                OverlayPage.None -> Unit
                            }
                        }
                    }
                }
            }
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
