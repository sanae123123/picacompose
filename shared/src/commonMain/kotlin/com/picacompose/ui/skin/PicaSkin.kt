package com.picacompose.ui.skin

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.picacompose.ui.components.PicaIcon
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 一套皮肤的完整定义。
 *
 * 分三块：外观数据 [tokens]、主题外壳 [Theme]、以及「用哪个引擎画控件」[engine]。
 * 加一套新皮肤通常只需要实现这个接口，填一份 token 选一个引擎，控件代码一行都不用写。
 *
 * 之所以还有「引擎」这一层：miuix 的 Button 和 Material3 的 Button 是两个不同库的类，
 * 一套代码没法同时是它们。皮肤可以只换数据（同一引擎），要换控件库才需要新增引擎。
 */
interface PicaSkin {
    /** 持久化用的稳定标识，别改。 */
    val id: String

    /** 设置页里显示的名字。 */
    val label: String

    /** 设置页里的一句话说明。 */
    val description: String

    /** 用哪套控件库画控件。 */
    val engine: SkinEngine

    /** 圆角、卡片底色这类外观数值。 */
    val tokens: SkinTokens

    /** 把颜色和文字样式铺给整棵树。 */
    @Composable
    fun Theme(darkTheme: Boolean, content: @Composable () -> Unit)
}

/** 控件引擎。 */
enum class SkinEngine(val id: String) {
    Miuix("miuix"),
    Material3("material3"),
}

/**
 * 皮肤的外观数据。
 *
 * 默认值就是 MIUI 风格那套；Material You 和以后的新皮肤按需覆盖。
 * [cardColor] 读的是 miuix 色板（Material You 皮肤下这份色板已经映射成 MD3 的颜色），
 * 所以拿到的永远是当前皮肤的颜色，不用各写一份。
 */
data class SkinTokens(
    val cardRadius: Dp = 28.dp,
    val rowRadius: Dp = 22.dp,
    val chipRadius: Dp = 999.dp,
    val fieldRadius: Dp = 14.dp,
    val cardTonalElevation: Dp = 0.dp,
    val cardShadow: Dp = 0.dp,
    val cardColor: @Composable () -> Color = { MiuixTheme.colorScheme.tertiaryContainer },
)

/** 底栏的一颗入口。 */
data class BottomBarTab(val label: String, val icon: PicaIcon)

/**
 * 引擎要实现的那批控件。页面和 [PicaSkin] 都只认这个接口，不直接碰具体组件库。
 *
 * 参数都显式写全，默认值放在对外的 `Pica*` 函数里，这样第三方皮肤实现引擎时不会被默认值绑死。
 */
internal interface SkinRenderer {
    @Composable
    fun Surface(
        modifier: Modifier,
        shape: Shape,
        color: Color,
        contentColor: Color,
        tonalElevation: Dp,
        shadowElevation: Dp,
        content: @Composable () -> Unit,
    )

    @Composable
    fun Button(
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        cornerRadius: Dp,
        content: @Composable RowScope.() -> Unit,
    )

    @Composable
    fun TextButton(
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        content: @Composable RowScope.() -> Unit,
    )

    @Composable
    fun OutlinedButton(
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        content: @Composable RowScope.() -> Unit,
    )

    @Composable
    fun IconButton(
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        content: @Composable () -> Unit,
    )

    @Composable
    fun Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier, enabled: Boolean)

    @Composable
    fun Checkbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier, enabled: Boolean)

    @Composable
    fun CircularProgress(modifier: Modifier, strokeWidth: Dp, progress: Float?, color: Color)

    @Composable
    fun LinearProgress(progress: Float, modifier: Modifier, color: Color, trackColor: Color)

    @Composable
    fun Scaffold(
        modifier: Modifier,
        containerColor: Color,
        bottomBar: @Composable () -> Unit,
        content: @Composable (PaddingValues) -> Unit,
    )

    @Composable
    fun Divider(modifier: Modifier)

    @Composable
    fun AlertDialog(
        onDismissRequest: () -> Unit,
        modifier: Modifier,
        title: (@Composable () -> Unit)?,
        text: (@Composable () -> Unit)?,
        confirmButton: @Composable () -> Unit,
        dismissButton: (@Composable () -> Unit)?,
    )

    @Composable
    fun FilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit)

    @Composable
    fun InputChip(
        selected: Boolean,
        onClick: () -> Unit,
        label: @Composable () -> Unit,
        trailingIcon: (@Composable () -> Unit)?,
    )

    @Composable
    fun TextField(
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
        keyboardOptions: KeyboardOptions,
        keyboardActions: KeyboardActions,
        visualTransformation: VisualTransformation,
    )

    @Composable
    fun DropdownMenu(
        expanded: Boolean,
        onDismissRequest: () -> Unit,
        content: @Composable ColumnScope.() -> Unit,
    )

    @Composable
    fun DropdownMenuItem(
        text: @Composable () -> Unit,
        onClick: () -> Unit,
        modifier: Modifier,
        leadingIcon: (@Composable () -> Unit)?,
        trailingIcon: (@Composable () -> Unit)?,
        enabled: Boolean,
    )

    /** 页面顶部的大标题。 */
    @Composable
    fun PageTitle(text: String, modifier: Modifier)

    /** 小信息胶囊。 */
    @Composable
    fun InfoChip(text: String)

    /** 整页底色。 */
    @Composable
    fun Backdrop(modifier: Modifier)

    /** 下拉刷新容器。 */
    @Composable
    fun PullRefresh(onRefresh: (suspend () -> Unit)?, modifier: Modifier, content: @Composable () -> Unit)

    /** 底部导航栏。 */
    @Composable
    fun BottomBar(tabs: List<BottomBarTab>, selectedIndex: Int, onSelect: (Int) -> Unit)
}

/** 当前皮肤该用哪个引擎。 */
internal val PicaSkin.renderer: SkinRenderer
    get() = when (engine) {
        SkinEngine.Miuix -> MiuixRenderer
        SkinEngine.Material3 -> Material3Renderer
    }
