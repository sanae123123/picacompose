package com.picacompose.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/** iOS 上同样取不到「应用图标」，调用方退回自绘字标。 */
@Composable
actual fun rememberAppIcon(): ImageBitmap? = null
