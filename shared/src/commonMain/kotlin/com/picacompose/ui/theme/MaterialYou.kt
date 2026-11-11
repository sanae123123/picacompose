package com.picacompose.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.Colors

/**
 * Material You 的色板来源。
 *
 * Android 实机用系统动态取色（跟着壁纸走）；桌面和 iOS 没有这套系统 API，退回 MD3 的
 * 基线色板 —— 那本来就是 Material You 的默认配色，观感是同一个体系的。
 */
@Composable
expect fun materialYouScheme(darkTheme: Boolean): ColorScheme

internal fun baselineMaterialYouScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) darkColorScheme() else lightColorScheme()

/**
 * 把 MD3 的 [ColorScheme] 铺到 miuix 的 [Colors] 上。
 *
 * 我们界面里的「卡片」用的是 miuix 的 `tertiaryContainer`，这里把它对到 MD3 的
 * `surfaceContainerHigh`（带色调的表面层），所以 Material You 皮肤下卡片会变成
 * MD3 那种淡紫 / 淡蓝的 tinted surface，而不是一片灰。
 */
fun ColorScheme.toMiuixColors(): Colors = Colors(
    primary = primary,
    onPrimary = onPrimary,
    primaryVariant = primary,
    onPrimaryVariant = primaryContainer,
    error = error,
    onError = onError,
    errorContainer = errorContainer,
    onErrorContainer = onErrorContainer,
    disabledPrimary = onSurface.copy(alpha = 0.12f),
    disabledOnPrimary = onSurface.copy(alpha = 0.38f),
    disabledPrimaryButton = onSurface.copy(alpha = 0.12f),
    disabledOnPrimaryButton = onSurface.copy(alpha = 0.38f),
    disabledPrimarySlider = onSurface.copy(alpha = 0.12f),
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondaryContainer,
    onSecondary = onSecondaryContainer,
    secondaryVariant = secondaryContainer,
    onSecondaryVariant = onSecondaryContainer,
    disabledSecondary = surfaceContainerHighest,
    disabledOnSecondary = onSurface.copy(alpha = 0.38f),
    disabledSecondaryVariant = surfaceContainerHighest,
    disabledOnSecondaryVariant = onSurface.copy(alpha = 0.38f),
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    secondaryContainerVariant = secondaryContainer,
    onSecondaryContainerVariant = onSecondaryContainer,
    // 界面里的卡片底：用 MD3 的带色调表面层。
    tertiaryContainer = surfaceContainerHigh,
    onTertiaryContainer = onSurfaceVariant,
    tertiaryContainerVariant = surfaceContainerHigh,
    background = background,
    onBackground = onBackground,
    onBackgroundVariant = onSurfaceVariant,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceContainerLow,
    onSurfaceSecondary = onSurfaceVariant,
    onSurfaceVariantSummary = onSurfaceVariant.copy(alpha = 0.74f),
    onSurfaceVariantActions = onSurfaceVariant.copy(alpha = 0.5f),
    disabledOnSurface = onSurface.copy(alpha = 0.38f),
    surfaceContainer = surfaceContainerLow,
    onSurfaceContainer = onSurface,
    onSurfaceContainerVariant = onSurfaceVariant,
    surfaceContainerHigh = surfaceContainerHigh,
    onSurfaceContainerHigh = onSurface,
    surfaceContainerHighest = surfaceContainerHighest,
    onSurfaceContainerHighest = onSurface,
    outline = outlineVariant,
    dividerLine = outlineVariant,
    windowDimming = Color.Black.copy(alpha = 0.32f),
    sliderKeyPoint = primary,
    sliderKeyPointForeground = onPrimary,
    sliderBackground = surfaceContainerHighest,
)
