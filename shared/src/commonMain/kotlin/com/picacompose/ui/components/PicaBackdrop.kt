package com.picacompose.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.picacompose.ui.skin.SkinRegistry
import com.picacompose.ui.skin.renderer

/**
 * 整页底色，按皮肤走：
 *
 * - MIUI 风格：HyperOS / MIUI 是纯色浅灰底。
 * - Material You：MD3 那种大面积低饱和渐变，上面叠两团柔光，深色模式自动跟着变。
 */
@Composable
fun PicaBackdrop(modifier: Modifier = Modifier) {
    SkinRegistry.current.renderer.Backdrop(modifier = modifier)
}
