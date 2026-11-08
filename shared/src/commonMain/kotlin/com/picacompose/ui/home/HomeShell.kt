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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.picacompose.data.pica
import com.picacompose.picaapi.model.UserProfile
import com.picacompose.ui.components.PicaBackdrop
import com.picacompose.ui.components.PicaGlyph

/**
 * 登录之后的壳：底部五个入口 + 对应内容。
 *
 * 各页自己管滚动和内边距，这里只负责切换和底部栏。
 */
@Composable
fun HomeShell(profile: UserProfile, onSignOut: () -> Unit) {
    var current by rememberSaveable { mutableStateOf(HomeTab.Home.name) }
    val tab = HomeTab.entries.firstOrNull { it.name == current } ?: HomeTab.Home
    // 放这里让页面切走再回来时保留已加载的分页和滚动位置。
    val gamePager = remember { Pager { page -> pica.games.list(page = page) } }
    val gameListState = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        PicaBackdrop()
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                PicaNavigationBar(selected = tab, onSelect = { current = it.name })
            },
        ) { inner ->
            Box(Modifier.fillMaxSize().padding(inner)) {
                when (tab) {
                    HomeTab.Home -> HomeContent(
                        profile = profile,
                        onOpenCategory = { current = HomeTab.Category.name },
                        onOpenGame = { current = HomeTab.Game.name },
                    )

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
