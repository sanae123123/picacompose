package com.picacompose.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Material 组件换到 miuix 之后，原来用 Material3 的几处需要一层薄薄的适配：
 * miuix 的 TextButton 只收字符串，弹窗和标签条也没有现成同名组件。
 * 全部集中在这里，等 miuix 版本升级时只改这一个文件。
 */

/** 透明底、主色文字的文字按钮，对应原来 Material3 的 TextButton。 */
@Composable
fun PicaTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        minWidth = 0.dp,
        minHeight = 36.dp,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        colors = ButtonDefaults.buttonColors(
            color = Color.Transparent,
            disabledColor = Color.Transparent,
            contentColor = colors.primary,
            disabledContentColor = colors.disabledPrimary,
        ),
        content = content,
    )
}

/** 沿用 Material3 AlertDialog 的写法，但换成 miuix 的配色（白底、主色标题、正文用次级色）。 */
@Composable
fun PicaAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: @Composable () -> Unit = {},
    dismissButton: (@Composable () -> Unit)? = null,
) {
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

/** 筛选器里的选项小胶囊。 */
@Composable
fun PicaFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) colors.primary.copy(alpha = 0.15f) else colors.tertiaryContainer,
        contentColor = if (selected) colors.primary else colors.onTertiaryContainer,
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

/** 已选标签的小胶囊，带一个删除图标。 */
@Composable
fun PicaInputChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) colors.primary.copy(alpha = 0.15f) else colors.tertiaryContainer,
        contentColor = if (selected) colors.primary else colors.onTertiaryContainer,
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
    cornerRadius: Dp = 14.dp,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
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

/** 下拉菜单：沿用 Material3 的弹出位置，容器换成 miuix 的卡片色。 */
@Composable
fun PicaDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        containerColor = colors.surfaceVariant,
        content = content,
    )
}

/** 菜单项：文字和图标都按 miuix 的色板来。 */
@Composable
fun PicaDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
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

/** 描边按钮：白底、细边、主色文字，对应原来 Material3 的 OutlinedButton。 */
@Composable
fun PicaOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier.border(1.dp, colors.primary.copy(alpha = 0.35f), RoundedCornerShape(999.dp)),
        enabled = enabled,
        cornerRadius = 999.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(
            color = colors.tertiaryContainer,
            disabledColor = colors.tertiaryContainer,
            contentColor = colors.primary,
            disabledContentColor = colors.disabledOnSurface,
        ),
        content = content,
    )
}
