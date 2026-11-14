package com.picacompose.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/** 桌面端没有可以取的应用图标，调用方退回自绘字标。 */
@Composable
actual fun rememberAppIcon(): ImageBitmap? = null
