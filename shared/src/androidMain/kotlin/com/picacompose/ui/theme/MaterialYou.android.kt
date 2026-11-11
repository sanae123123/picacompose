package com.picacompose.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * Material You 皮肤固定用 MD3 基线色板（那套紫色）。
 *
 * 之前试过 Android 12+ 的壁纸动态取色，但换了壁纸整个 App 颜色就飘了，
 * 跟「切换到 Material You」这个预期不一致，所以回到基线色板。
 */
@Composable
actual fun materialYouScheme(darkTheme: Boolean): ColorScheme = baselineMaterialYouScheme(darkTheme)
