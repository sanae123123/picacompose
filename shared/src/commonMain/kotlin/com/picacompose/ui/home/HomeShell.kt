package com.picacompose.ui.home

import androidx.compose.animation.AnimatedContent
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
import com.picacompose.ui.components.PicaBackdrop
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.picaPagePush
import kotlinx.coroutines.launch

/** 盖在主界面上的整页：官方那种从右边推进来的二级页。 */
private enum class OverlayPage { None, Messages, Forum }

/**
 * 登录之后的壳：底部五个入口 + 对应内容。
 *
 * 页面用一个横向 pager 装：点底部栏滑过去，左右拖动也能翻页，翻页动画由 pager 自己带。
 * 各页自己管滚动和内边距，这里只负责切换和底部栏。
 *
 * 消息页、留言板是「点按钮打开的整页」，盖在底部栏之上、整屏推入（见 [OverlayPage]），
 * 这样进出都有一整屏的动画，跟官方一致。
 */
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
    val overlay = OverlayPage.entries.getOrElse(overlayOrdinal) { OverlayPage.None }
    // 消息是主页里的一层，没有独立的底部入口；退出去回到首页。
    val messagePager = remember { Pager { page -> pica.users.notifications(page = page) } }
    val messageListState = rememberLazyListState()
    // 留言板挂在分类页里，同样提上来：退出去再进来不重拉第一页。
    val forumPager = rememberCommentPager(FORUM_COMIC_ID)
    val forumListState = rememberLazyListState()
    // 首页那条流也提上来：打开消息页再退回来时，不重拉推荐、不跳回顶部。
    val homeListState = rememberLazyListState()
    val homeFeed = rememberHomeFeedState()
    // 放这里让页面切走再回来时保留已加载的分页和滚动位置。
    val gamePager = remember { Pager { page -> pica.games.list(page = page) } }
    val gameListState = rememberLazyStaggeredGridState()

    // 各栏的滚动位置在滑走再回来时也能接上（懒列表的状态本身是可保存的）。
    val tabStates = rememberSaveableStateHolder()

    // 整页开着的时候，系统返回先退回上一屏，而不是直接退出 App。
    BackHandlerCompat(enabled = overlay != OverlayPage.None) {
        overlayOrdinal = OverlayPage.None.ordinal
    }

    Box(Modifier.fillMaxSize()) {
        PicaBackdrop()
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
                userScrollEnabled = overlay == OverlayPage.None,
                key = { tabs[it].name },
            ) { page ->
                tabStates.SaveableStateProvider(tabs[page].name) {
                    when (tabs[page]) {
                        HomeTab.Home -> HomeContent(
                            profile = profile,
                            listState = homeListState,
                            feed = homeFeed,
                            onOpenMessages = { overlayOrdinal = OverlayPage.Messages.ordinal },
                        )

                        HomeTab.Category -> CategoriesContent(
                            onOpenForum = { overlayOrdinal = OverlayPage.Forum.ordinal },
                            onOpenGames = {
                                overlayOrdinal = OverlayPage.None.ordinal
                                scope.launch { pagerState.animateScrollToPage(HomeTab.Game.ordinal) }
                            },
                        )

                        HomeTab.Game -> GamesContent(pager = gamePager, listState = gameListState)
                        HomeTab.Mine -> MineContent(profile = profile, onSignOut = onSignOut)
                        HomeTab.Settings -> SettingsContent(onSignOut = onSignOut)
                    }
                }
            }
        }

        // 整页层：盖住底部栏，从右边整屏推入；关掉时再整屏滑回去。
        AnimatedContent(
            targetState = overlay,
            transitionSpec = { picaPagePush { it != OverlayPage.None } },
            modifier = Modifier.fillMaxSize(),
            label = "overlayPage",
        ) { page ->
            if (page == OverlayPage.None) {
                Box(Modifier.fillMaxSize())
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    // 二级页也铺同一块渐变底，看起来是同一套皮。
                    PicaBackdrop()
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                        when (page) {
                            OverlayPage.Messages -> MessagesContent(
                                pager = messagePager,
                                listState = messageListState,
                                onBack = { overlayOrdinal = OverlayPage.None.ordinal },
                            )

                            OverlayPage.Forum -> ForumContent(
                                pager = forumPager,
                                listState = forumListState,
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
