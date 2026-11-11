package com.picacompose.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Android 12 起跟着壁纸动态取色；更老的系统没有这套 API，用 MD3 基线色板。
 */
@Composable
actual fun materialYouScheme(darkTheme: Boolean): ColorScheme {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        return if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    return baselineMaterialYouScheme(darkTheme)
}
