package com.picacompose.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** iOS 这边没有系统动态取色，用 MD3 基线色板。 */
@Composable
actual fun materialYouScheme(darkTheme: Boolean): ColorScheme = baselineMaterialYouScheme(darkTheme)
