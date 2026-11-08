package com.picacompose.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/** iOS 端还没打包官方磁贴图，先返回 null。 */
@Composable
actual fun hotTileArt(name: String): Painter? = null
