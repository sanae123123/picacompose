package com.picacompose.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** 桌面没有系统动态取色，直接用 MD3 基线色板。 */
@Composable
actual fun materialYouScheme(darkTheme: Boolean): ColorScheme = baselineMaterialYouScheme(darkTheme)
