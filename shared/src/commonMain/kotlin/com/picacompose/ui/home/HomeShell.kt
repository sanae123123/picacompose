package com.picacompose.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import kotlinx.coroutines.launch

/**
 * 登录之后的壳：底部五个入口 + 对应内容。
 *
 * 页面用一个横向 pager 装：点底部栏滑过去，左右拖动也能翻页，翻页动画由 pager 自己带。
 * 各页自己管滚动和内边距，这里只负责切换和底部栏。
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
    // 消息是主页里的一层，没有独立的底部入口；退出去回到首页。
    var showMessages by rememberSaveable { mutableStateOf(false) }
    val messagePager = remember { Pager { page -> pica.users.notifications(page = page) } }
    val messageListState = rememberLazyListState()
    // 放这里让页面切走再回来时保留已加载的分页和滚动位置。
    val gamePager = remember { Pager { page -> pica.games.list(page = page) } }
    val gameListState = rememberLazyStaggeredGridState()

    // 消息页是主页里的一层，系统返回先退回首页，而不是直接退出 App。
    BackHandlerCompat(enabled = showMessages) { showMessages = false }

    Box(Modifier.fillMaxSize()) {
        PicaBackdrop()
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                PicaNavigationBar(
                    selected = tab,
                    onSelect = {
                        showMessages = false
                        scope.launch { pagerState.animateScrollToPage(it.ordinal) }
                    },
                )
            },
        ) { inner ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().padding(inner),
                // 消息页盖在主页那一栏上，这时候别让横滑把整页翻走。
                userScrollEnabled = !showMessages,
                key = { tabs[it].name },
            ) { page ->
                when (tabs[page]) {
                    HomeTab.Home -> if (showMessages) {
                        MessagesContent(
                            pager = messagePager,
                            listState = messageListState,
                            onBack = { showMessages = false },
                        )
                    } else {
                        HomeContent(
                            profile = profile,
                            onOpenMessages = { showMessages = true },
                        )
                    }

                    HomeTab.Category -> CategoriesContent()
                    HomeTab.Game -> GamesContent(pager = gamePager, listState = gameListState)
                    HomeTab.Mine -> MineContent(profile = profile, onSignOut = onSignOut)
                    HomeTab.Settings -> SettingsContent(onSignOut = onSignOut)
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
