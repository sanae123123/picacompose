package com.picacompose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 页面底色：直接铺 miuix 的 background 色。
 *
 * 以前是 Material You 那种大面积低饱和渐变加深色光斑，HyperOS / MIUI 是纯色浅灰底，
 * 留渐变反而更不像，所以这里只做一层纯色。
 */
@Composable
fun PicaBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(MiuixTheme.colorScheme.background))
}
