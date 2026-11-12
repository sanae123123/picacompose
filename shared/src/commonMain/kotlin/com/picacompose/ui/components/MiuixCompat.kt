package com.picacompose.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.picacompose.ui.theme.SkinTokens
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 两套皮肤共用的语义化组件。
 *
 * MIUI 风格用 miuix 的控件，Material You 用 Material3 的控件，页面只认这个文件里的名字。
 * 颜色都从 [MiuixTheme] 取（Material You 皮肤下那份色板本来就是 MD3 的动态色），
 * 所以切换皮肤时控件形态和配色会一起变，不用改上层逻辑。
 */

/** 弹窗：两个皮肤各用自己家的 AlertDialog。 */
@Composable
fun PicaAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: @Composable () -> Unit = {},
    dismissButton: (@Composable () -> Unit)? = null,
) {
    if (md3Skin) {
        val colors = MaterialTheme.colorScheme
        AlertDialog(
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
    } else {
        val colors = MiuixTheme.colorScheme
        AlertDialog(
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
}

/** 筛选器里的选项小胶囊。 */
@Composable
fun PicaFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
) {
    if (md3Skin) {
        val colors = MaterialTheme.colorScheme
        FilterChip(
            selected = selected,
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            label = {
                MiuixTextColor(if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant) {
                    label()
                }
            },
        )
    } else {
        val colors = MiuixTheme.colorScheme
        // 选中用实心的主色、未选中是白底加一圈描边：筛选弹窗本身是浅灰底，
        // 两边都淡淡的会看不出哪个是选中的。
        Surface(
            shape = RoundedCornerShape(SkinTokens.chipRadius),
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
}

/** 已选标签的小胶囊，带一个删除图标。 */
@Composable
fun PicaInputChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    if (md3Skin) {
        val colors = MaterialTheme.colorScheme
        InputChip(
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
    } else {
        val colors = MiuixTheme.colorScheme
        Surface(
            shape = RoundedCornerShape(SkinTokens.chipRadius),
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
}

/** 输入框。miuix 的占位文字用 label + useLabelAsPlaceholder，这里收成一个 placeholder 参数。 */
@Composable
fun PicaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    textStyle: TextStyle = MiuixTheme.textStyles.main,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    cornerRadius: Dp = SkinTokens.fieldRadius,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    if (md3Skin) {
        val colors = MaterialTheme.colorScheme
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle,
            placeholder = placeholder.takeIf { it.isNotEmpty() }?.let { hint ->
                { MiuixTextColor(colors.onSurfaceVariant) { Text(hint, style = textStyle) } }
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
    } else {
        TextField(
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
}

/** 下拉菜单：MIUI 用 miuix 的卡片色，Material You 用 MD3 的菜单底色。 */
@Composable
fun PicaDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (md3Skin) {
        DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest, content = content)
    } else {
        val colors = MiuixTheme.colorScheme
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            containerColor = colors.surfaceVariant,
            content = content,
        )
    }
}

/** 菜单项：文字和图标都按当前皮肤的色板来。 */
@Composable
fun PicaDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    if (md3Skin) {
        val colors = MaterialTheme.colorScheme
        DropdownMenuItem(
            text = { MiuixTextColor(colors.onSurface) { text() } },
            onClick = onClick,
            modifier = modifier,
            leadingIcon = leadingIcon?.let { icon -> { MiuixTextColor(colors.onSurfaceVariant) { icon() } } },
            trailingIcon = trailingIcon?.let { icon -> { MiuixTextColor(colors.onSurfaceVariant) { icon() } } },
            enabled = enabled,
        )
    } else {
        val colors = MiuixTheme.colorScheme
        DropdownMenuItem(
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
}
