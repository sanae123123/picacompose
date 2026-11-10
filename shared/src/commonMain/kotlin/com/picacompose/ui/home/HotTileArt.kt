package com.picacompose.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/**
 * 「热门分类」磁贴用的官方图。
 *
 * Android 用的是从官方包里抠出来、放进 res/drawable-nodpi 的那套 cat_*.jpg；
 * 桌面 / iOS 暂时没打包，返回 null 时磁贴退化成底色 + 标题。
 */
@Composable
expect fun hotTileArt(name: String): Painter?
