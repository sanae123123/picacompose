package com.picacompose.ui.skin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog as Md3AlertDialog
import androidx.compose.material3.DropdownMenu as Md3DropdownMenu
import androidx.compose.material3.DropdownMenuItem as Md3DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider as Md3HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator as Md3LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.picacompose.ui.components.MiuixTextColor
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import io.github.alexzhirkevich.cupertino.CupertinoActivityIndicator
import io.github.alexzhirkevich.cupertino.CupertinoButton
import io.github.alexzhirkevich.cupertino.CupertinoButtonDefaults
import io.github.alexzhirkevich.cupertino.CupertinoButtonSize
import io.github.alexzhirkevich.cupertino.CupertinoCheckBox
import io.github.alexzhirkevich.cupertino.CupertinoHorizontalDivider
import io.github.alexzhirkevich.cupertino.CupertinoIconButton
import io.github.alexzhirkevich.cupertino.CupertinoScaffold
import io.github.alexzhirkevich.cupertino.CupertinoSurface
import io.github.alexzhirkevich.cupertino.CupertinoSwitch
import io.github.alexzhirkevich.cupertino.CupertinoText
import io.github.alexzhirkevich.cupertino.CupertinoTextField
import io.github.alexzhirkevich.cupertino.ExperimentalCupertinoApi
import io.github.alexzhirkevich.cupertino.theme.CupertinoTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * iOS 引擎：控件全部用 Compose Cupertino 的组件，剩下的壳自己画。
 *
 * 开关、按钮、图标按钮、勾选、输入框、分隔线、卡片底、页面容器、加载圈都是
 * Cupertino 的（这正是苹果那套实现），所以形态、尺寸、按压反馈都是 iOS 原样。
 * 库里没有对应物的（底栏、分段筛选、弹窗、下拉、信息胶囊）由这里补，
 * 补出来的样子也按 iOS 的画。
 */
@OptIn(ExperimentalCupertinoApi::class)
internal object IosRenderer : SkinRenderer {

    @Composable
    private fun mdColors() = MaterialTheme.colorScheme

    @Composable
    private fun iosColors() = CupertinoTheme.colorScheme

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
        CupertinoSurface(
            modifier = modifier,
            shape = shape,
            color = if (color == Color.Unspecified) colors.systemBackground else color,
            shadowElevation = shadowElevation,
            contentColor = if (contentColor == Color.Unspecified) colors.label else contentColor,
        ) {
            content()
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
        CupertinoButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            size = CupertinoButtonSize.Regular,
            colors = CupertinoButtonDefaults.filledButtonColors(
                contentColor = Color.White,
                containerColor = colors.accent,
            ),
            shape = RoundedCornerShape(minOf(cornerRadius, 12.dp)),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            content = content,
        )
    }

    @Composable
    override fun TextButton(
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        content: @Composable RowScope.() -> Unit,
    ) {
        val colors = iosColors()
        CupertinoButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            size = CupertinoButtonSize.Regular,
            colors = CupertinoButtonDefaults.plainButtonColors(contentColor = colors.accent),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            content = content,
        )
    }

    @Composable
    override fun OutlinedButton(
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        content: @Composable RowScope.() -> Unit,
    ) {
        val colors = iosColors()
        CupertinoButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            size = CupertinoButtonSize.Regular,
            colors = CupertinoButtonDefaults.filledButtonColors(
                contentColor = colors.accent,
                containerColor = colors.systemBackground,
            ),
            border = BorderStroke(1.dp, colors.separator),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 11.dp),
            content = content,
        )
    }

    @Composable
    override fun IconButton(
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        content: @Composable () -> Unit,
    ) {
        val colors = iosColors()
        CupertinoIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = CupertinoButtonDefaults.plainButtonColors(contentColor = colors.accent),
            content = content,
        )
    }

    @Composable
    override fun Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier, enabled: Boolean) {
        CupertinoSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
        )
    }

    @Composable
    override fun Checkbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier, enabled: Boolean) {
        CupertinoCheckBox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
        )
    }

    @Composable
    override fun CircularProgress(modifier: Modifier, strokeWidth: Dp, progress: Float?, color: Color) {
        if (progress == null) {
            CupertinoActivityIndicator(modifier = modifier, color = color)
        } else {
            CupertinoActivityIndicator(
                modifier = modifier,
                progress = progress.coerceIn(0f, 1f),
                color = color,
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
        CupertinoScaffold(
            modifier = modifier,
            bottomBar = bottomBar,
            containerColor = if (containerColor == Color.Unspecified) colors.systemGroupedBackground else containerColor,
            contentColor = colors.label,
            content = content,
        )
    }

    @Composable
    override fun Divider(modifier: Modifier) {
        CupertinoHorizontalDivider(
            modifier = modifier,
            thickness = 0.5.dp,
            color = iosColors().separator,
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
        val colors = mdColors()
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
        val colors = mdColors()
        val shape = RoundedCornerShape(9.dp)
        Box(
            modifier = Modifier
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
        val colors = mdColors()
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
        CupertinoTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle,
            placeholder = if (placeholder.isNotEmpty()) {
                { CupertinoText(text = placeholder, style = textStyle) }
            } else {
                null
            },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
        )
    }

    @Composable
    override fun DropdownMenu(
        expanded: Boolean,
        onDismissRequest: () -> Unit,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        val colors = mdColors()
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
        val colors = mdColors()
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
        CupertinoText(
            text = text,
            modifier = modifier,
            style = CupertinoTheme.typography.largeTitle,
            fontWeight = FontWeight.Bold,
            color = iosColors().label,
        )
    }

    @Composable
    override fun InfoChip(text: String) {
        val colors = mdColors()
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
        Box(modifier.fillMaxSize().background(iosColors().systemGroupedBackground))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun PullRefresh(onRefresh: (suspend () -> Unit)?, modifier: Modifier, content: @Composable () -> Unit) {
        if (onRefresh == null) {
            Box(modifier) { content() }
            return
        }
        val colors = mdColors()
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
        Column(Modifier.navigationBarsPadding()) {
            Md3HorizontalDivider(color = colors.separator)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.systemBackground)
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
                            if (active) colors.accent else colors.tertiaryLabel,
                        )
                        Spacer(Modifier.height(2.dp))
                        MiuixTextColor(if (active) colors.accent else colors.tertiaryLabel) {
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
