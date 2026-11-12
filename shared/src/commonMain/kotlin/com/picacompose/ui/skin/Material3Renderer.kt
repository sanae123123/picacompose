package com.picacompose.ui.skin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog as Md3AlertDialog
import androidx.compose.material3.Button as Md3Button
import androidx.compose.material3.ButtonDefaults as Md3ButtonDefaults
import androidx.compose.material3.Checkbox as Md3Checkbox
import androidx.compose.material3.CircularProgressIndicator as Md3CircularProgressIndicator
import androidx.compose.material3.DropdownMenu as Md3DropdownMenu
import androidx.compose.material3.DropdownMenuItem as Md3DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip as Md3FilterChip
import androidx.compose.material3.HorizontalDivider as Md3HorizontalDivider
import androidx.compose.material3.Icon as Md3Icon
import androidx.compose.material3.IconButton as Md3IconButton
import androidx.compose.material3.InputChip as Md3InputChip
import androidx.compose.material3.LinearProgressIndicator as Md3LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar as Md3NavigationBar
import androidx.compose.material3.NavigationBarItem as Md3NavigationBarItem
import androidx.compose.material3.OutlinedButton as Md3OutlinedButton
import androidx.compose.material3.OutlinedTextField as Md3OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold as Md3Scaffold
import androidx.compose.material3.Surface as Md3Surface
import androidx.compose.material3.Switch as Md3Switch
import androidx.compose.material3.Text as Md3Text
import androidx.compose.material3.TextButton as Md3TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.picacompose.ui.components.MiuixTextColor
import com.picacompose.ui.components.vector
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Material You 引擎：控件全用 Material3 的，配色和字体也走 MD3。
 *
 * 里层还套着 miuix 的 `MiuixTheme`（底色映射成 MD3 色板），所以 miuix 的 Text / PicaGlyph
 * 在 MD3 控件里也能拿到正确颜色——靠 [MiuixTextColor] 桥接过去。
 */
internal object Material3Renderer : SkinRenderer {

    @Composable
    override fun Surface(
        modifier: Modifier,
        shape: Shape,
        color: Color,
        contentColor: Color,
        tonalElevation: Dp,
        shadowElevation: Dp,
        content: @Composable () -> Unit,
    ) {
        val colors = MaterialTheme.colorScheme
        val resolved = if (contentColor == Color.Unspecified) contentColorFor(color) else contentColor
        // 旧版 Material3 界面里 surface 底的卡片带 1dp tonalElevation，卡片和背景才分得开。
        val elevation = if (tonalElevation == 0.dp && color == colors.surface) 1.dp else tonalElevation
        Md3Surface(
            modifier = modifier,
            shape = shape,
            color = color,
            contentColor = resolved,
            tonalElevation = elevation,
            shadowElevation = shadowElevation,
        ) {
            MiuixTextColor(resolved) { content() }
        }
    }

    @Composable
    override fun Button(
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        cornerRadius: Dp,
        content: @Composable RowScope.() -> Unit,
    ) {
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
    }

    @Composable
    override fun TextButton(
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        content: @Composable RowScope.() -> Unit,
    ) {
        val colors = MaterialTheme.colorScheme
        Md3TextButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            content = { MiuixTextColor(colors.primary) { content() } },
        )
    }

    @Composable
    override fun OutlinedButton(
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        content: @Composable RowScope.() -> Unit,
    ) {
        val colors = MaterialTheme.colorScheme
        Md3OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            content = { MiuixTextColor(colors.primary) { content() } },
        )
    }

    @Composable
    override fun IconButton(
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        content: @Composable () -> Unit,
    ) {
        val colors = MaterialTheme.colorScheme
        Md3IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            MiuixTextColor(colors.onSurfaceVariant) { content() }
        }
    }

    @Composable
    override fun Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier, enabled: Boolean) {
        Md3Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
    }

    @Composable
    override fun Checkbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier, enabled: Boolean) {
        Md3Checkbox(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
    }

    @Composable
    override fun CircularProgress(modifier: Modifier, strokeWidth: Dp, progress: Float?, color: Color) {
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
    }

    @Composable
    override fun LinearProgress(progress: Float, modifier: Modifier, color: Color, trackColor: Color) {
        Md3LinearProgressIndicator(
            progress = { progress },
            modifier = modifier,
            color = color,
            trackColor = trackColor,
        )
    }

    @Composable
    override fun Scaffold(
        modifier: Modifier,
        containerColor: Color,
        bottomBar: @Composable () -> Unit,
        content: @Composable (PaddingValues) -> Unit,
    ) {
        Md3Scaffold(
            modifier = modifier,
            containerColor = containerColor,
            bottomBar = bottomBar,
        ) { inner ->
            val contentColor = MiuixTheme.colorScheme.onBackground
            MiuixTextColor(contentColor) { content(inner) }
        }
    }

    @Composable
    override fun Divider(modifier: Modifier) {
        Md3HorizontalDivider(
            modifier = modifier,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }

    @Composable
    override fun AlertDialog(
        onDismissRequest: () -> Unit,
        modifier: Modifier,
        title: (@Composable () -> Unit)?,
        text: (@Composable () -> Unit)?,
        confirmButton: @Composable () -> Unit,
        dismissButton: (@Composable () -> Unit)?,
    ) {
        val colors = MaterialTheme.colorScheme
        Md3AlertDialog(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            containerColor = colors.surfaceContainerHigh,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurfaceVariant,
            title = title?.let { content -> { MiuixTextColor(colors.onSurface) { content() } } },
            text = text?.let { content -> { MiuixTextColor(colors.onSurfaceVariant) { content() } } },
            confirmButton = { MiuixTextColor(colors.primary) { confirmButton() } },
            dismissButton = dismissButton?.let { content -> { MiuixTextColor(colors.primary) { content() } } },
        )
    }

    @Composable
    override fun FilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
        val colors = MaterialTheme.colorScheme
        Md3FilterChip(
            selected = selected,
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            label = {
                MiuixTextColor(if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant) {
                    label()
                }
            },
        )
    }

    @Composable
    override fun InputChip(
        selected: Boolean,
        onClick: () -> Unit,
        label: @Composable () -> Unit,
        trailingIcon: (@Composable () -> Unit)?,
    ) {
        val colors = MaterialTheme.colorScheme
        Md3InputChip(
            selected = selected,
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            label = {
                MiuixTextColor(if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant) {
                    label()
                }
            },
            trailingIcon = trailingIcon?.let { icon ->
                { MiuixTextColor(colors.onSurfaceVariant) { icon() } }
            },
        )
    }

    @Composable
    override fun TextField(
        value: String,
        onValueChange: (String) -> Unit,
        modifier: Modifier,
        placeholder: String,
        textStyle: TextStyle,
        singleLine: Boolean,
        maxLines: Int,
        minLines: Int,
        cornerRadius: Dp,
        enabled: Boolean,
        readOnly: Boolean,
        leadingIcon: (@Composable () -> Unit)?,
        trailingIcon: (@Composable () -> Unit)?,
        keyboardOptions: androidx.compose.foundation.text.KeyboardOptions,
        keyboardActions: androidx.compose.foundation.text.KeyboardActions,
        visualTransformation: androidx.compose.ui.text.input.VisualTransformation,
    ) {
        val colors = MaterialTheme.colorScheme
        Md3OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle,
            placeholder = placeholder.takeIf { it.isNotEmpty() }?.let { hint ->
                { MiuixTextColor(colors.onSurfaceVariant) { Md3Text(hint, style = textStyle) } }
            },
            shape = RoundedCornerShape(4.dp),
            leadingIcon = leadingIcon?.let { icon ->
                { MiuixTextColor(colors.onSurfaceVariant) { icon() } }
            },
            trailingIcon = trailingIcon?.let { icon ->
                { MiuixTextColor(colors.onSurfaceVariant) { icon() } }
            },
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
        )
    }

    @Composable
    override fun DropdownMenu(
        expanded: Boolean,
        onDismissRequest: () -> Unit,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        Md3DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest, content = content)
    }

    @Composable
    override fun DropdownMenuItem(
        text: @Composable () -> Unit,
        onClick: () -> Unit,
        modifier: Modifier,
        leadingIcon: (@Composable () -> Unit)?,
        trailingIcon: (@Composable () -> Unit)?,
        enabled: Boolean,
    ) {
        val colors = MaterialTheme.colorScheme
        Md3DropdownMenuItem(
            text = { MiuixTextColor(colors.onSurface) { text() } },
            onClick = onClick,
            modifier = modifier,
            leadingIcon = leadingIcon?.let { icon -> { MiuixTextColor(colors.onSurfaceVariant) { icon() } } },
            trailingIcon = trailingIcon?.let { icon -> { MiuixTextColor(colors.onSurfaceVariant) { icon() } } },
            enabled = enabled,
        )
    }

    @Composable
    override fun PageTitle(text: String, modifier: Modifier) {
        Md3Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = modifier,
        )
    }

    @Composable
    override fun InfoChip(text: String) {
        val colors = MaterialTheme.colorScheme
        Md3Surface(
            shape = CircleShape,
            color = colors.secondaryContainer.copy(alpha = 0.85f),
            contentColor = colors.onSecondaryContainer,
        ) {
            Md3Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSecondaryContainer,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }

    @Composable
    override fun Backdrop(modifier: Modifier) {
        val colors = MaterialTheme.colorScheme
        Canvas(modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colors.primaryContainer.copy(alpha = 0.85f),
                        colors.background,
                    ),
                    startY = 0f,
                    endY = size.height * 0.62f,
                ),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.tertiaryContainer.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(size.width * 0.92f, size.height * 0.08f),
                    radius = size.width * 0.55f,
                ),
                radius = size.width * 0.55f,
                center = Offset(size.width * 0.92f, size.height * 0.08f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.secondaryContainer.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(size.width * 0.02f, size.height * 0.72f),
                    radius = size.width * 0.60f,
                ),
                radius = size.width * 0.60f,
                center = Offset(size.width * 0.02f, size.height * 0.72f),
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun PullRefresh(onRefresh: (suspend () -> Unit)?, modifier: Modifier, content: @Composable () -> Unit) {
        if (onRefresh == null) {
            Box(modifier) { content() }
            return
        }
        val scope = rememberCoroutineScope()
        var refreshing by remember { mutableStateOf(false) }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                if (!refreshing) {
                    scope.launch {
                        refreshing = true
                        runCatching { onRefresh() }
                        refreshing = false
                    }
                }
            },
            modifier = modifier,
        ) {
            content()
        }
    }

    @Composable
    override fun BottomBar(tabs: List<BottomBarTab>, selectedIndex: Int, onSelect: (Int) -> Unit) {
        val md3 = MaterialTheme.colorScheme
        Md3NavigationBar(containerColor = md3.surfaceContainer) {
            tabs.forEachIndexed { index, tab ->
                val active = index == selectedIndex
                Md3NavigationBarItem(
                    selected = active,
                    onClick = { onSelect(index) },
                    icon = {
                        Md3Icon(
                            imageVector = tab.icon.vector(active),
                            contentDescription = tab.label,
                            tint = if (active) md3.onSecondaryContainer else md3.onSurfaceVariant,
                        )
                    },
                    label = {
                        Md3Text(
                            text = tab.label,
                            style = MiuixTheme.textStyles.footnote2,
                            color = if (active) md3.onSurface else md3.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }
}
