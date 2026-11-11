package com.picacompose.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * HyperOS / MIUI 观感：用 miuix 的默认色板（白色背景 + MIUI 蓝主色 + 大圆角分段列表）。
 *
 * miuix 是 Compose Multiplatform 库，三端都支持；主题色以后要换成 Pica 粉可以从
 * lightColorScheme(primary = ...) 里改。
 */
private val LightColors = lightColorScheme()

private val DarkColors = darkColorScheme()

@Composable
fun PicaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MiuixTheme(
        colors = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
