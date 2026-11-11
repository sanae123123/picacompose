package com.picacompose.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button as Md3Button
import androidx.compose.material3.ButtonDefaults as Md3ButtonDefaults
import androidx.compose.material3.Checkbox as Md3Checkbox
import androidx.compose.material3.CircularProgressIndicator as Md3CircularProgressIndicator
import androidx.compose.material3.IconButton as Md3IconButton
import androidx.compose.material3.LinearProgressIndicator as Md3LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.OutlinedButton as Md3OutlinedButton
import androidx.compose.material3.Switch as Md3Switch
import androidx.compose.material3.TextButton as Md3TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.picacompose.ui.theme.AppSkin
import com.picacompose.ui.theme.SkinState
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox as MiuixCheckbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator as MiuixLinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixLocalContentColor
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.state.ToggleableState

/**
 * 一套按皮肤选实现的基础控件。
 *
 * 页面只写一份逻辑，调 [PicaButton]、[PicaSwitch] 这些；里面会看当前皮肤：
 * MIUI 风格用 miuix 的组件，Material You 用 Material3 的组件。两套都从同一份
 * 色板取色，所以切换皮肤时颜色和形状一起换。
 *
 * 唯一的坑是内容颜色：miuix 的 Text/Icon 读 miuix 自己的 `LocalContentColor`，
 * Material3 组件提供的是 Material3 那一份。所以 Material3 分支里会用
 * [MiuixTextColor] 把颜色桥接给 miuix 的文字和图标。
 */
internal val md3Skin: Boolean
    get() = SkinState.current == AppSkin.MaterialYou

/** Material3 分支里，把颜色喂给 miuix 的 Text / Icon / PicaGlyph。 */
@Composable
internal fun MiuixTextColor(color: Color, content: @Composable () -> Unit) {
    CompositionLocalProvider(MiuixLocalContentColor provides color, content = content)
}

/** 表面层：页面里的卡片、弹窗底、胶囊底都用它。 */
@Composable
fun PicaSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = MiuixTheme.colorScheme.surface,
    contentColor: Color = Color.Unspecified,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    if (md3Skin) {
        val colors = MaterialTheme.colorScheme
        val resolved = if (contentColor == Color.Unspecified) contentColorFor(color) else contentColor
        // 旧版 Material3 界面里，surface 底的卡片都带 1dp tonalElevation，卡片和背景才分得开。
        val elevation = if (tonalElevation == 0.dp && color == colors.surface) 1.dp else tonalElevation
        androidx.compose.material3.Surface(
            modifier = modifier,
            shape = shape,
            color = color,
            contentColor = resolved,
            tonalElevation = elevation,
            shadowElevation = shadowElevation,
        ) {
            MiuixTextColor(resolved) { content() }
        }
    } else {
        if (contentColor == Color.Unspecified) {
            MiuixSurface(
                modifier = modifier,
                shape = shape,
                color = color,
                shadowElevation = shadowElevation,
                content = content,
            )
        } else {
            MiuixSurface(
                modifier = modifier,
                shape = shape,
                color = color,
                contentColor = contentColor,
                shadowElevation = shadowElevation,
                content = content,
            )
        }
    }
}

/** 主按钮：MIUI 用 miuix 的实心按钮，MD3 用 Material3 的 FilledButton。 */
@Composable
fun PicaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = 999.dp,
    content: @Composable RowScope.() -> Unit,
) {
    if (md3Skin) {
        val colors = MaterialTheme.colorScheme
        Md3Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = RoundedCornerShape(cornerRadius),
            colors = Md3ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                disabledContainerColor = colors.onSurface.copy(alpha = 0.12f),
                disabledContentColor = colors.onSurface.copy(alpha = 0.38f),
            ),
            content = { MiuixTextColor(colors.onPrimary) { content() } },
        )
    } else {
        val colors = MiuixTheme.colorScheme
        MiuixButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            cornerRadius = cornerRadius,
            colors = MiuixButtonDefaults.buttonColors(
                color = colors.primary,
                contentColor = colors.onPrimary,
            ),
            content = content,
        )
    }
}

/** 文字按钮：没有底色，只有主色文字。 */
@Composable
fun PicaTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    if (md3Skin) {
        val colors = MaterialTheme.colorScheme
        Md3TextButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            content = { MiuixTextColor(colors.primary) { content() } },
        )
    } else {
        val colors = MiuixTheme.colorScheme
        MiuixButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            minWidth = 0.dp,
            minHeight = 36.dp,
            insideMargin = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            colors = MiuixButtonDefaults.buttonColors(
                color = Color.Transparent,
                disabledColor = Color.Transparent,
                contentColor = colors.primary,
                disabledContentColor = colors.disabledPrimary,
            ),
            content = content,
        )
    }
}

/** 描边按钮：细边 + 主色文字。 */
@Composable
fun PicaOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    if (md3Skin) {
        val colors = MaterialTheme.colorScheme
        Md3OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            content = { MiuixTextColor(colors.primary) { content() } },
        )
    } else {
        val colors = MiuixTheme.colorScheme
        MiuixButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            cornerRadius = 999.dp,
            insideMargin = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            colors = MiuixButtonDefaults.buttonColors(
                color = colors.tertiaryContainer,
                disabledColor = colors.tertiaryContainer,
                contentColor = colors.primary,
                disabledContentColor = colors.disabledOnSurface,
            ),
            content = content,
        )
    }
}

/** 图标按钮：两个皮肤都是 40dp 的圆形点击区。 */
@Composable
fun PicaIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (md3Skin) {
        val colors = MaterialTheme.colorScheme
        Md3IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            MiuixTextColor(colors.onSurfaceVariant) { content() }
        }
    } else {
        MiuixIconButton(onClick = onClick, modifier = modifier, content = content)
    }
}

/** 开关。 */
@Composable
fun PicaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (md3Skin) {
        Md3Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
    } else {
        MiuixSwitch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
    }
}

/** 复选框。 */
@Composable
fun PicaCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (md3Skin) {
        Md3Checkbox(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
    } else {
        MiuixCheckbox(
            state = ToggleableState(checked),
            onClick = { onCheckedChange(!checked) },
            modifier = modifier,
            enabled = enabled,
        )
    }
}

/** 圆形进度：不确定进度时传 null。 */
@Composable
fun PicaCircularProgress(
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 4.dp,
    progress: Float? = null,
    color: Color = MiuixTheme.colorScheme.primary,
) {
    if (md3Skin) {
        if (progress == null) {
            Md3CircularProgressIndicator(modifier = modifier, color = color, strokeWidth = strokeWidth)
        } else {
            Md3CircularProgressIndicator(
                progress = { progress },
                modifier = modifier,
                color = color,
                strokeWidth = strokeWidth,
            )
        }
    } else {
        MiuixCircularProgressIndicator(
            modifier = modifier,
            strokeWidth = strokeWidth,
            progress = progress,
            colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = color),
        )
    }
}

/** 条形进度。 */
@Composable
fun PicaLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.primary,
    trackColor: Color = MiuixTheme.colorScheme.surfaceVariant,
) {
    if (md3Skin) {
        Md3LinearProgressIndicator(
            progress = { progress },
            modifier = modifier,
            color = color,
            trackColor = trackColor,
        )
    } else {
        MiuixLinearProgressIndicator(
            progress = progress,
            modifier = modifier,
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = color,
                backgroundColor = trackColor,
            ),
        )
    }
}
