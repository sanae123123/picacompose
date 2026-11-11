package com.picacompose.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 一种皮肤，两套控件实现。
 *
 * 组件层（`ui/components` 里的 Pica* 组件）按当前皮肤挑控件：MIUI 风格用 miuix 的，
 * Material You 用 Material3 的。两个皮肤都同时提供 [MiuixTheme] 和 [MaterialTheme]，
 * 这样两边的控件都拿到同一份色板——MIUI 皮肤下 MaterialTheme 是从 miuix 色板反推的，
 * Material You 皮肤下 miuix 色板是从 MD3 色板映射的。
 */
private val MiuixLightColors = lightColorScheme()

private val MiuixDarkColors = darkColorScheme()

@Composable
fun PicaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    when (SkinState.current) {
        AppSkin.Miuix -> {
            val miuixColors = if (darkTheme) MiuixDarkColors else MiuixLightColors
            val md3Colors = remember(miuixColors, darkTheme) { miuixColors.toMd3Scheme(darkTheme) }
            MaterialTheme(colorScheme = md3Colors) {
                MiuixTheme(colors = miuixColors, content = content)
            }
        }

        AppSkin.MaterialYou -> {
            val scheme = materialYouScheme(darkTheme)
            MaterialTheme(colorScheme = scheme) {
                MiuixTheme(
                    colors = remember(scheme) { scheme.toMiuixColors() },
                    textStyles = md3MiuixTextStyles(),
                ) {
                    content()
                }
            }
        }
    }
}
