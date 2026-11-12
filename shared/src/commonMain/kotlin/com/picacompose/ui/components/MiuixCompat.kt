package com.picacompose.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import com.picacompose.ui.skin.SkinRegistry
import com.picacompose.ui.skin.renderer
import com.picacompose.ui.theme.SkinTokens
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 语义化控件：页面只认这些名字，具体长什么样由当前皮肤决定。
 *
 * 每个函数都是转发壳，实现分别在 `ui/skin` 的两个引擎里。
 */

/** 弹窗。 */
@Composable
fun PicaAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: @Composable () -> Unit = {},
    dismissButton: (@Composable () -> Unit)? = null,
) {
    SkinRegistry.current.renderer.AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
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
    SkinRegistry.current.renderer.FilterChip(selected = selected, onClick = onClick, label = label)
}

/** 已选标签的小胶囊，带一个删除图标。 */
@Composable
fun PicaInputChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    SkinRegistry.current.renderer.InputChip(
        selected = selected,
        onClick = onClick,
        label = label,
        trailingIcon = trailingIcon,
    )
}

/** 输入框。占位文字统一收成一个 placeholder 参数。 */
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
    SkinRegistry.current.renderer.TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
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

/** 下拉菜单。 */
@Composable
fun PicaDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    SkinRegistry.current.renderer.DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        content = content,
    )
}

/** 菜单项。 */
@Composable
fun PicaDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    SkinRegistry.current.renderer.DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
    )
}
