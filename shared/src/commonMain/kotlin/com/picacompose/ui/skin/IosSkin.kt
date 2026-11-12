package com.picacompose.ui.skin

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.picacompose.ui.theme.toMiuixColors
import com.slapps.cupertino.ExperimentalCupertinoApi
import com.slapps.cupertino.theme.CupertinoTheme
import com.slapps.cupertino.theme.darkColorScheme as cupertinoDarkColorScheme
import com.slapps.cupertino.theme.lightColorScheme as cupertinoLightColorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * iOS 风格。
 *
 * 控件全部走 [IosRenderer] 单独画的 iOS 形态，配色用 iOS 系统色：
 * 浅色是灰底白卡片，深色是纯黑底深灰卡片。字体没动。
 */
private fun iosScheme(darkTheme: Boolean) = if (darkTheme) {
    darkColorScheme(
        primary = Color(0xFF0A84FF),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF003A6E),
        onPrimaryContainer = Color(0xFFD1E4FF),
        secondaryContainer = Color(0xFF2C2C2E),
        onSecondaryContainer = Color(0xFFE5E5EA),
        tertiaryContainer = Color(0xFF1C1C1E),
        onTertiaryContainer = Color(0xFFEBEBF5),
        background = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF1C1C1E),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF1C1C1E),
        onSurfaceVariant = Color(0xFF8E8E93),
        surfaceContainerLow = Color(0xFF1C1C1E),
        surfaceContainer = Color(0xFF1C1C1E),
        surfaceContainerHigh = Color(0xFF2C2C2E),
        surfaceContainerHighest = Color(0xFF3A3A3C),
        outline = Color(0xFF545458),
        outlineVariant = Color(0xFF38383A),
        error = Color(0xFFFF453A),
        onError = Color.White,
        errorContainer = Color(0xFF5A1A14),
        onErrorContainer = Color(0xFFFFB4AB),
    )
} else {
    lightColorScheme(
        primary = Color(0xFF007AFF),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD6E4FF),
        onPrimaryContainer = Color(0xFF00316B),
        secondaryContainer = Color(0xFFE5E5EA),
        onSecondaryContainer = Color(0xFF000000),
        tertiaryContainer = Color(0xFFFFFFFF),
        onTertiaryContainer = Color(0xFF000000),
        background = Color(0xFFF2F2F7),
        onBackground = Color(0xFF000000),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF000000),
        surfaceVariant = Color(0xFFF2F2F7),
        onSurfaceVariant = Color(0xFF8E8E93),
        surfaceContainerLow = Color(0xFFFFFFFF),
        surfaceContainer = Color(0xFFF2F2F7),
        surfaceContainerHigh = Color(0xFFFFFFFF),
        surfaceContainerHighest = Color(0xFFE5E5EA),
        outline = Color(0xFFC6C6C8),
        outlineVariant = Color(0xFFC6C6C8),
        error = Color(0xFFFF3B30),
        onError = Color.White,
        errorContainer = Color(0xFFFFE5E0),
        onErrorContainer = Color(0xFF7A1B0C),
    )
}

object IosSkin : PicaSkin {
    override val id: String = "ios"
    override val label: String = "iOS 风格"
    override val description: String = "iOS 的灰底白卡片、绿色开关和分段筛选，控件全部重画。"
    override val engine: SkinEngine = SkinEngine.Ios
    override val tokens: SkinTokens = SkinTokens(
        cardRadius = 10.dp,
        rowRadius = 10.dp,
        fieldRadius = 10.dp,
        cardColor = { MaterialTheme.colorScheme.surface },
    )

    @OptIn(ExperimentalCupertinoApi::class)
    @Composable
    override fun Theme(darkTheme: Boolean, content: @Composable () -> Unit) {
        val scheme = remember(darkTheme) { iosScheme(darkTheme) }
        val iosScheme = remember(darkTheme) {
            if (darkTheme) cupertinoDarkColorScheme() else cupertinoLightColorScheme()
        }
        MaterialTheme(colorScheme = scheme) {
            MiuixTheme(colors = remember(scheme) { scheme.toMiuixColors() }) {
                CupertinoTheme(colorScheme = iosScheme, content = content)
            }
        }
    }
}
