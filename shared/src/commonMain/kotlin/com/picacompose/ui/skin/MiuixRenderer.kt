package com.picacompose.ui.skin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog as Md3AlertDialog
import androidx.compose.material3.DropdownMenu as Md3DropdownMenu
import androidx.compose.material3.DropdownMenuItem as Md3DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.picacompose.ui.components.vector
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox as MiuixCheckbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider as MiuixHorizontalDivider
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator as MiuixLinearProgressIndicator
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.PullToRefresh as MiuixPullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * MIUI / HyperOS 引擎：控件全用 miuix 的。
 *
 * 颜色和文字样式由主题层铺好，这里只管形态；圆角这类数值从当前皮肤的 token 取，
 * 所以换个 token 不用动这个文件。
 */
internal object MiuixRenderer : SkinRenderer {

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

    @Composable
    override fun Button(
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        cornerRadius: Dp,
        content: @Composable RowScope.() -> Unit,
    ) {
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

    @Composable
    override fun TextButton(
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        content: @Composable RowScope.() -> Unit,
    ) {
        val colors = MiuixTheme.colorScheme
        MiuixButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            minWidth = 0.dp,
            minHeight = 36.dp,
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            colors = MiuixButtonDefaults.buttonColors(
                color = Color.Transparent,
                disabledColor = Color.Transparent,
                contentColor = colors.primary,
                disabledContentColor = colors.disabledPrimary,
            ),
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
        val colors = MiuixTheme.colorScheme
        MiuixButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            cornerRadius = 999.dp,
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            colors = MiuixButtonDefaults.buttonColors(
                color = colors.tertiaryContainer,
                disabledColor = colors.tertiaryContainer,
                contentColor = colors.primary,
                disabledContentColor = colors.disabledOnSurface,
            ),
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
        MiuixIconButton(onClick = onClick, modifier = modifier, content = content)
    }

    @Composable
    override fun Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier, enabled: Boolean) {
        MiuixSwitch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
    }

    @Composable
    override fun Checkbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier, enabled: Boolean) {
        MiuixCheckbox(
            state = ToggleableState(checked),
            onClick = { onCheckedChange(!checked) },
            modifier = modifier,
            enabled = enabled,
        )
    }

    @Composable
    override fun CircularProgress(modifier: Modifier, strokeWidth: Dp, progress: Float?, color: Color) {
        MiuixCircularProgressIndicator(
            modifier = modifier,
            strokeWidth = strokeWidth,
            progress = progress,
            colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = color),
        )
    }

    @Composable
    override fun LinearProgress(progress: Float, modifier: Modifier, color: Color, trackColor: Color) {
        MiuixLinearProgressIndicator(
            progress = progress,
            modifier = modifier,
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = color,
                backgroundColor = trackColor,
            ),
        )
    }

    @Composable
    override fun Scaffold(
        modifier: Modifier,
        containerColor: Color,
        bottomBar: @Composable () -> Unit,
        content: @Composable (PaddingValues) -> Unit,
    ) {
        MiuixScaffold(
            modifier = modifier,
            containerColor = if (containerColor == Color.Unspecified) MiuixTheme.colorScheme.background else containerColor,
            bottomBar = bottomBar,
            content = content,
        )
    }

    @Composable
    override fun Divider(modifier: Modifier) {
        MiuixHorizontalDivider(modifier = modifier)
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
        val colors = MiuixTheme.colorScheme
        Md3AlertDialog(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            containerColor = colors.surfaceVariant,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurfaceSecondary,
            title = title,
            text = text,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
        )
    }

    @Composable
    override fun FilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
        val colors = MiuixTheme.colorScheme
        // 选中用实心主色、未选中白底加描边：筛选弹窗本身是浅灰底，两边都淡会看不出选没选中。
        MiuixSurface(
            shape = RoundedCornerShape(SkinRegistry.current.tokens.chipRadius),
            color = if (selected) colors.primary else colors.surface,
            contentColor = if (selected) colors.onPrimary else colors.onSurface,
            border = if (selected) null else BorderStroke(1.dp, colors.dividerLine),
            modifier = Modifier.clickable(onClick = onClick),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                label()
            }
        }
    }

    @Composable
    override fun InputChip(
        selected: Boolean,
        onClick: () -> Unit,
        label: @Composable () -> Unit,
        trailingIcon: (@Composable () -> Unit)?,
    ) {
        val colors = MiuixTheme.colorScheme
        MiuixSurface(
            shape = RoundedCornerShape(SkinRegistry.current.tokens.chipRadius),
            color = colors.surface,
            contentColor = colors.onSurface,
            border = BorderStroke(1.dp, colors.dividerLine),
            modifier = Modifier.clickable(onClick = onClick),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column { label() }
                trailingIcon?.invoke()
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
        MiuixTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            label = placeholder,
            useLabelAsPlaceholder = true,
            textStyle = textStyle,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            cornerRadius = cornerRadius,
            enabled = enabled,
            readOnly = readOnly,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
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
        Md3DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            containerColor = MiuixTheme.colorScheme.surfaceVariant,
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
        val colors = MiuixTheme.colorScheme
        Md3DropdownMenuItem(
            text = text,
            onClick = onClick,
            modifier = modifier,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            enabled = enabled,
            colors = MenuDefaults.itemColors(
                textColor = colors.onSurface,
                leadingIconColor = colors.onSurfaceSecondary,
                trailingIconColor = colors.onSurfaceSecondary,
                disabledTextColor = colors.disabledOnSurface,
                disabledLeadingIconColor = colors.disabledOnSurface,
                disabledTrailingIconColor = colors.disabledOnSurface,
            ),
        )
    }

    @Composable
    override fun PageTitle(text: String, modifier: Modifier) {
        MiuixText(
            text = text,
            style = MiuixTheme.textStyles.title2,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = modifier,
        )
    }

    @Composable
    override fun InfoChip(text: String) {
        val colors = MiuixTheme.colorScheme
        MiuixSurface(
            shape = RoundedCornerShape(SkinRegistry.current.tokens.chipRadius),
            color = colors.tertiaryContainer,
        ) {
            MiuixText(
                text = text,
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onTertiaryContainer,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }

    @Composable
    override fun Backdrop(modifier: Modifier) {
        Box(modifier.fillMaxSize().background(MiuixTheme.colorScheme.background))
    }

    @Composable
    override fun PullRefresh(onRefresh: (suspend () -> Unit)?, modifier: Modifier, content: @Composable () -> Unit) {
        if (onRefresh == null) {
            Box(modifier) { content() }
            return
        }
        val scope = rememberCoroutineScope()
        var refreshing by remember { mutableStateOf(false) }
        MiuixPullToRefresh(
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
        val colors = MiuixTheme.colorScheme
        MiuixNavigationBar(color = colors.surfaceVariant.copy(alpha = 0.96f)) {
            tabs.forEachIndexed { index, tab ->
                val active = index == selectedIndex
                MiuixNavigationBarItem(
                    selected = active,
                    onClick = { onSelect(index) },
                    icon = tab.icon.vector(active),
                    label = tab.label,
                )
            }
        }
    }
}
