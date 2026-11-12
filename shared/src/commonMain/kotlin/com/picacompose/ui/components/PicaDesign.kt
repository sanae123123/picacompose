package com.picacompose.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.picacompose.ui.skin.SkinRegistry
import com.picacompose.ui.skin.renderer
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixLocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 页面用的基础控件。
 *
 * 每个函数都是一层壳：皮肤决定样式和它用哪套控件库，这里只负责转发。
 * 加一套新皮肤不用改这个文件，除非新皮肤要换第三家控件库（那时在 `ui/skin` 里加一个引擎）。
 *
 * 唯一的坑是内容颜色：miuix 的 Text/Icon 读 miuix 自己的 `LocalContentColor`，
 * Material3 组件提供的是 Material3 那一份，所以 Material3 引擎里会用 [MiuixTextColor]
 * 把颜色桥接给 miuix 的文字和图标。
 */

/** Material3 引擎里，把颜色喂给 miuix 的 Text / Icon / PicaGlyph。 */
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
    SkinRegistry.current.renderer.Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        content = content,
    )
}

/** 主按钮。 */
@Composable
fun PicaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = 999.dp,
    content: @Composable RowScope.() -> Unit,
) {
    SkinRegistry.current.renderer.Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        cornerRadius = cornerRadius,
        content = content,
    )
}

/** 文字按钮：没有底色，只有主色文字。 */
@Composable
fun PicaTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    SkinRegistry.current.renderer.TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        content = content,
    )
}

/** 描边按钮：细边 + 主色文字。 */
@Composable
fun PicaOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    SkinRegistry.current.renderer.OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        content = content,
    )
}

/** 图标按钮：40dp 的圆形点击区。 */
@Composable
fun PicaIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    SkinRegistry.current.renderer.IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        content = content,
    )
}

/** 开关。 */
@Composable
fun PicaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SkinRegistry.current.renderer.Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
    )
}

/** 复选框。 */
@Composable
fun PicaCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SkinRegistry.current.renderer.Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
    )
}

/** 圆形进度：不确定进度时传 null。 */
@Composable
fun PicaCircularProgress(
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 4.dp,
    progress: Float? = null,
    color: Color = MiuixTheme.colorScheme.primary,
) {
    SkinRegistry.current.renderer.CircularProgress(
        modifier = modifier,
        strokeWidth = strokeWidth,
        progress = progress,
        color = color,
    )
}

/** 条形进度。 */
@Composable
fun PicaLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.primary,
    trackColor: Color = MiuixTheme.colorScheme.surfaceVariant,
) {
    SkinRegistry.current.renderer.LinearProgress(
        progress = progress,
        modifier = modifier,
        color = color,
        trackColor = trackColor,
    )
}

/** 页面骨架。 */
@Composable
fun PicaScaffold(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Unspecified,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    SkinRegistry.current.renderer.Scaffold(
        modifier = modifier,
        containerColor = containerColor,
        bottomBar = bottomBar,
        content = content,
    )
}

/** 分割线。 */
@Composable
fun PicaDivider(modifier: Modifier = Modifier) {
    SkinRegistry.current.renderer.Divider(modifier = modifier)
}
