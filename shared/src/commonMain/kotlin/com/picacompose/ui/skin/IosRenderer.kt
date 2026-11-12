package com.picacompose.ui.skin

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog as Md3AlertDialog
import androidx.compose.material3.CircularProgressIndicator as Md3CircularProgressIndicator
import androidx.compose.material3.DropdownMenu as Md3DropdownMenu
import androidx.compose.material3.DropdownMenuItem as Md3DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider as Md3HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator as Md3LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold as Md3Scaffold
import androidx.compose.material3.Text as Md3Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.picacompose.ui.components.MiuixTextColor
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.vector
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** iOS 开关的那种绿。 */
private val IosGreen = Color(0xFF34C759)

/**
 * iOS 引擎：控件全部按 iOS 的样子重画。
 *
 * 不借 miuix / Material3 的控件形态，只从 [MaterialTheme] 取颜色。所以这套皮肤下
 * 开关是绿色胶囊、勾选是圆形蓝勾、筛选条是分段的灰底白块、底栏是 iOS 的 TabBar 样子，
 * 而不是换了颜色的 Android 控件。
 *
 * 点按反馈也按 iOS 来：不加涟漪，只做了裁剪和按压区域。
 */
internal object IosRenderer : SkinRenderer {

    @Composable
    private fun iosColors() = MaterialTheme.colorScheme

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
        val colors = iosColors()
        val fill = if (color == Color.Unspecified) colors.surface else color
        val onFill = if (contentColor == Color.Unspecified) colors.onSurface else contentColor
        Box(
            modifier = modifier
                .shadow(shadowElevation, shape)
                .clip(shape)
                .background(fill),
        ) {
            MiuixTextColor(onFill) { content() }
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
        val colors = iosColors()
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(minOf(cornerRadius, 12.dp)))
                .background(if (enabled) colors.primary else colors.primary.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                ) { onClick() }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiuixTextColor(colors.onPrimary) { content() }
        }
    }

    @Composable
    override fun TextButton(
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        content: @Composable RowScope.() -> Unit,
    ) {
        val colors = iosColors()
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                ) { onClick() }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiuixTextColor(if (enabled) colors.primary else colors.onSurfaceVariant) { content() }
        }
    }

    @Composable
    override fun OutlinedButton(
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        content: @Composable RowScope.() -> Unit,
    ) {
        val colors = iosColors()
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                ) { onClick() }
                .padding(horizontal = 18.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiuixTextColor(if (enabled) colors.primary else colors.onSurfaceVariant) { content() }
        }
    }

    @Composable
    override fun IconButton(
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        content: @Composable () -> Unit,
    ) {
        val colors = iosColors()
        Box(
            modifier = modifier
                .defaultMinSize(40.dp, 40.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                ) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            MiuixTextColor(colors.primary) { content() }
        }
    }

    @Composable
    override fun Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier, enabled: Boolean) {
        val colors = iosColors()
        val track by animateColorAsState(
            if (checked) IosGreen else colors.outline.copy(alpha = 0.45f),
        )
        val thumbOffset by animateDpAsState(if (checked) 20.dp else 0.dp)
        Box(
            modifier = modifier
                .size(width = 51.dp, height = 31.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(track)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                ) { onCheckedChange(!checked) }
                .padding(2.dp),
        ) {
            Box(
                Modifier
                    .offset(x = thumbOffset)
                    .size(27.dp)
                    .shadow(2.dp, CircleShape)
                    .background(Color.White, CircleShape),
            )
        }
    }

    @Composable
    override fun Checkbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier, enabled: Boolean) {
        val colors = iosColors()
        Box(
            modifier = modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) colors.primary else Color.Transparent)
                .border(1.5.dp, if (checked) colors.primary else colors.outline, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                ) { onCheckedChange(!checked) },
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                PicaGlyph(PicaIcon.Check, Modifier.size(14.dp), colors.onPrimary)
            }
        }
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
        val colors = iosColors()
        Md3Scaffold(
            modifier = modifier,
            containerColor = if (containerColor == Color.Unspecified) colors.background else containerColor,
            bottomBar = bottomBar,
        ) { inner ->
            MiuixTextColor(colors.onBackground) { content(inner) }
        }
    }

    @Composable
    override fun Divider(modifier: Modifier) {
        val colors = iosColors()
        Box(
            modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(colors.outlineVariant),
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
        val colors = iosColors()
        Md3AlertDialog(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            containerColor = colors.surface,
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
        val colors = iosColors()
        val shape = RoundedCornerShape(9.dp)
        Box(
            modifier = Modifier
                .then(if (selected) Modifier.shadow(1.dp, shape) else Modifier)
                .clip(shape)
                .background(if (selected) colors.surface else colors.surfaceContainer)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClick() }
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            MiuixTextColor(if (selected) colors.onSurface else colors.onSurfaceVariant) { label() }
        }
    }

    @Composable
    override fun InputChip(
        selected: Boolean,
        onClick: () -> Unit,
        label: @Composable () -> Unit,
        trailingIcon: (@Composable () -> Unit)?,
    ) {
        val colors = iosColors()
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(colors.surfaceContainer)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClick() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiuixTextColor(colors.onSurface) { label() }
            if (trailingIcon != null) {
                MiuixTextColor(colors.onSurfaceVariant) { trailingIcon() }
            }
        }
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
        val colors = iosColors()
        val shape = RoundedCornerShape(minOf(cornerRadius, 12.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle.copy(color = colors.onSurface),
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            cursorBrush = SolidColor(colors.primary),
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .clip(shape)
                        .background(colors.surfaceContainer)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) {
                        MiuixTextColor(colors.onSurfaceVariant) { leadingIcon() }
                        Spacer(Modifier.width(8.dp))
                    }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            MiuixTextColor(colors.onSurfaceVariant) {
                                MiuixText(text = placeholder, style = textStyle)
                            }
                        }
                        inner()
                    }
                    if (trailingIcon != null) {
                        Spacer(Modifier.width(8.dp))
                        MiuixTextColor(colors.onSurfaceVariant) { trailingIcon() }
                    }
                }
            },
        )
    }

    @Composable
    override fun DropdownMenu(
        expanded: Boolean,
        onDismissRequest: () -> Unit,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        val colors = iosColors()
        Md3DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            shape = RoundedCornerShape(13.dp),
            containerColor = colors.surface,
            content = content,
        )
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
        val colors = iosColors()
        Md3DropdownMenuItem(
            text = { MiuixTextColor(colors.onSurface) { text() } },
            onClick = onClick,
            modifier = modifier,
            leadingIcon = leadingIcon?.let { icon -> { MiuixTextColor(colors.primary) { icon() } } },
            trailingIcon = trailingIcon?.let { icon -> { MiuixTextColor(colors.onSurfaceVariant) { icon() } } },
            enabled = enabled,
            colors = MenuDefaults.itemColors(
                textColor = colors.onSurface,
                disabledTextColor = colors.onSurfaceVariant.copy(alpha = 0.4f),
            ),
        )
    }

    @Composable
    override fun PageTitle(text: String, modifier: Modifier) {
        val colors = iosColors()
        Md3Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = colors.onBackground,
            modifier = modifier,
        )
    }

    @Composable
    override fun InfoChip(text: String) {
        val colors = iosColors()
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(colors.surfaceContainer)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            MiuixTextColor(colors.onSurfaceVariant) {
                MiuixText(
                    text = text,
                    style = MiuixTheme.textStyles.footnote1,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }

    @Composable
    override fun Backdrop(modifier: Modifier) {
        val colors = iosColors()
        Box(modifier.fillMaxSize().background(colors.background))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun PullRefresh(onRefresh: (suspend () -> Unit)?, modifier: Modifier, content: @Composable () -> Unit) {
        if (onRefresh == null) {
            Box(modifier) { content() }
            return
        }
        val colors = iosColors()
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
            MiuixTextColor(colors.onBackground) { content() }
        }
    }

    @Composable
    override fun BottomBar(tabs: List<BottomBarTab>, selectedIndex: Int, onSelect: (Int) -> Unit) {
        val colors = iosColors()
        Column {
            Md3HorizontalDivider(color = colors.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(vertical = 6.dp),
            ) {
                tabs.forEachIndexed { index, tab ->
                    val active = index == selectedIndex
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onSelect(index) }
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PicaGlyph(
                            tab.icon,
                            Modifier.size(24.dp),
                            if (active) colors.primary else colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))
                        MiuixTextColor(if (active) colors.primary else colors.onSurfaceVariant) {
                            MiuixText(
                                text = tab.label,
                                style = MiuixTheme.textStyles.footnote2,
                            )
                        }
                    }
                }
            }
        }
    }
}
