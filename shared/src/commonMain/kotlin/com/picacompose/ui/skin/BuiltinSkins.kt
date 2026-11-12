package com.picacompose.ui.skin

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.picacompose.ui.theme.md3MiuixTextStyles
import com.picacompose.ui.theme.materialYouScheme
import com.picacompose.ui.theme.toMd3Scheme
import com.picacompose.ui.theme.toMiuixColors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

private val MiuixLightColors = lightColorScheme()

private val MiuixDarkColors = darkColorScheme()

/**
 * MIUI / HyperOS 风格。
 *
 * 控件、圆角、配色都照着 HyperOS 来：白色底、淡蓝卡片、大圆角。
 */
object MiuixSkin : PicaSkin {
    override val id: String = "miuix"
    override val label: String = "MIUI 风格"
    override val description: String = "HyperOS 的控件和蓝色，纯色底加大圆角卡片。"
    override val engine: SkinEngine = SkinEngine.Miuix
    override val tokens: SkinTokens = SkinTokens()

    @Composable
    override fun Theme(darkTheme: Boolean, content: @Composable () -> Unit) {
        val miuixColors = if (darkTheme) MiuixDarkColors else MiuixLightColors
        val md3Colors = remember(miuixColors, darkTheme) { miuixColors.toMd3Scheme(darkTheme) }
        MaterialTheme(colorScheme = md3Colors) {
            MiuixTheme(colors = miuixColors, content = content)
        }
    }
}

/**
 * Material You。
 *
 * 控件换成 Material3 的，配色和字阶也换成 MD3 基线色板（那套紫色），
 * 底上叠一层低饱和的渐变光 —— 就是换 miuix 之前那版的观感。
 */
object MaterialYouSkin : PicaSkin {
    override val id: String = "material3"
    override val label: String = "Material You"
    override val description: String = "Material3 原生控件和字阶，MD3 基线配色。"
    override val engine: SkinEngine = SkinEngine.Material3
    override val tokens: SkinTokens = SkinTokens(
        rowRadius = 16.dp,
        fieldRadius = 4.dp,
        cardTonalElevation = 1.dp,
        cardColor = { MaterialTheme.colorScheme.surface },
    )

    @Composable
    override fun Theme(darkTheme: Boolean, content: @Composable () -> Unit) {
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
