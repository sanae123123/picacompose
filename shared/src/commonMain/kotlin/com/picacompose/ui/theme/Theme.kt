package com.picacompose.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.picacompose.ui.skin.SkinRegistry

/**
 * 皮肤入口。
 *
 * 具体铺什么颜色、什么字阶，由当前皮肤自己的 `Theme` 决定（见 `ui/skin/BuiltinSkins.kt`）。
 * 皮肤内部会同时提供 [MaterialTheme] 和 [top.yukonga.miuix.kmp.theme.MiuixTheme]，
 * 所以两边的控件都能拿到同一份色板。
 */
@Composable
fun PicaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    SkinRegistry.current.Theme(darkTheme, content)
}
