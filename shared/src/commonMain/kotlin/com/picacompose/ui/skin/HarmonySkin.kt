package com.picacompose.ui.skin

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.picacompose.ui.theme.toMiuixColors
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 鸿蒙的设计主色。 */
private val HarmonyBlue = Color(0xFF0A59F7)

/** 鸿蒙正文用的深墨色。 */
private val HarmonyInk = Color(0xFF182431)

private val HarmonyCardLight = Color(0xFFF1F3F5)

private val HarmonyCardDark = Color(0xFF1F1F1F)

/**
 * 鸿蒙风格。
 *
 * 只换外观：控件继续用 miuix 引擎，配色、卡片底色、圆角换成鸿蒙那套——
 * 白底、浅灰分组卡片、鸿蒙蓝、比 MIUI 更收敛的圆角。字体没动，还是现在这套。
 *
 * 鸿蒙的色板先摊成 MD3 的 [lightColorScheme] / [darkColorScheme]，再走
 * [toMiuixColors] 映射成 miuix 的色板，这样两边控件拿到的颜色是一致的。
 */
private fun harmonyScheme(darkTheme: Boolean) = if (darkTheme) {
    darkColorScheme(
        primary = Color(0xFF4B8AF8),
        onPrimary = Color(0xFF050505),
        primaryContainer = Color(0xFF0A2A5C),
        onPrimaryContainer = Color(0xFFD6E3FF),
        secondaryContainer = Color(0xFF1B2738),
        onSecondaryContainer = Color(0xFF9FC0FF),
        tertiaryContainer = HarmonyCardDark,
        onTertiaryContainer = Color(0xFFB3B3B3),
        background = Color(0xFF000000),
        onBackground = Color(0xFFE5E5E5),
        surface = Color(0xFF000000),
        onSurface = Color(0xFFE5E5E5),
        surfaceVariant = Color(0xFF1A1A1A),
        onSurfaceVariant = Color(0xFFB3B3B3),
        surfaceContainerLow = Color(0xFF141414),
        surfaceContainer = Color(0xFF1A1A1A),
        surfaceContainerHigh = HarmonyCardDark,
        surfaceContainerHighest = Color(0xFF2A2A2A),
        outline = Color(0xFF5A5A5A),
        outlineVariant = Color(0xFF2E2E2E),
        error = Color(0xFFE84026),
        onError = Color(0xFF1A0000),
        errorContainer = Color(0xFF4A1208),
        onErrorContainer = Color(0xFFFFB4A4),
    )
} else {
    lightColorScheme(
        primary = HarmonyBlue,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE6EEFF),
        onPrimaryContainer = Color(0xFF00307A),
        secondaryContainer = Color(0xFFE6EEFF),
        onSecondaryContainer = HarmonyBlue,
        tertiaryContainer = HarmonyCardLight,
        onTertiaryContainer = HarmonyInk,
        background = Color.White,
        onBackground = HarmonyInk,
        surface = Color.White,
        onSurface = HarmonyInk,
        surfaceVariant = Color(0xFFF5F6F7),
        onSurfaceVariant = Color(0x99182431),
        surfaceContainerLow = Color(0xFFF7F8FA),
        surfaceContainer = Color(0xFFF1F3F5),
        surfaceContainerHigh = HarmonyCardLight,
        surfaceContainerHighest = Color(0xFFE8EBEE),
        outline = Color(0xFF8A939C),
        outlineVariant = Color(0xFFE0E3E6),
        error = Color(0xFFE84026),
        onError = Color.White,
        errorContainer = Color(0xFFFFE5E0),
        onErrorContainer = Color(0xFF7A1B0C),
    )
}

object HarmonySkin : PicaSkin {
    override val id: String = "harmony"
    override val label: String = "鸿蒙风格"
    override val description: String = "鸿蒙蓝 + 白底浅灰分组卡片，圆角比 MIUI 收敛一些。"
    override val engine: SkinEngine = SkinEngine.Miuix
    override val tokens: SkinTokens = SkinTokens(
        cardRadius = 24.dp,
        rowRadius = 16.dp,
        fieldRadius = 16.dp,
    )

    @Composable
    override fun Theme(darkTheme: Boolean, content: @Composable () -> Unit) {
        val scheme = remember(darkTheme) { harmonyScheme(darkTheme) }
        MaterialTheme(colorScheme = scheme) {
            MiuixTheme(
                colors = remember(scheme) { scheme.toMiuixColors() },
                content = content,
            )
        }
    }
}
