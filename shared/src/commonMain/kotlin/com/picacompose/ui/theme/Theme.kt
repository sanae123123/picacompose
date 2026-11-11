package com.picacompose.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 两套皮肤共用同一套 miuix 组件，只换色板：
 *
 * - MIUI 风格：miuix 自带的色板（白色背景 + MIUI 蓝）。
 * - Material You：Android 12 起跟着壁纸动态取色，其余平台用 MD3 基线色板。
 *
 * 现在的界面里「卡片」用的是 `tertiaryContainer`，Material You 下会被映射成 MD3 的
 * 带色调表面层，所以切过去是 MD3 的观感，不是把组件换回原来那套。
 */
private val MiuixLightColors = lightColorScheme()

private val MiuixDarkColors = darkColorScheme()

@Composable
fun PicaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = when (SkinState.current) {
        AppSkin.Miuix -> if (darkTheme) MiuixDarkColors else MiuixLightColors
        AppSkin.MaterialYou -> {
            val scheme = materialYouScheme(darkTheme)
            remember(scheme) { scheme.toMiuixColors() }
        }
    }
    MiuixTheme(
        colors = colors,
        content = content,
    )
}
