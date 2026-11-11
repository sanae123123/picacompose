package com.picacompose.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.picacompose.ui.theme.SkinTokens
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 页面底色，按皮肤走：
 *
 * - MIUI 风格：HyperOS / MIUI 是纯色浅灰底，就铺一层 miuix 的 background。
 * - Material You：回到 MD3 那种大面积低饱和渐变，上面叠两团柔光，
 *   跟换 miuix 之前那版一样；深色模式下颜色自动跟着变。
 */
@Composable
fun PicaBackdrop(modifier: Modifier = Modifier) {
    if (SkinTokens.materialYou) {
        val colors = MaterialTheme.colorScheme
        Canvas(modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colors.primaryContainer.copy(alpha = 0.85f),
                        colors.background,
                    ),
                    startY = 0f,
                    endY = size.height * 0.62f,
                ),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.tertiaryContainer.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(size.width * 0.92f, size.height * 0.08f),
                    radius = size.width * 0.55f,
                ),
                radius = size.width * 0.55f,
                center = Offset(size.width * 0.92f, size.height * 0.08f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.secondaryContainer.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(size.width * 0.02f, size.height * 0.72f),
                    radius = size.width * 0.60f,
                ),
                radius = size.width * 0.60f,
                center = Offset(size.width * 0.02f, size.height * 0.72f),
            )
        }
    } else {
        Box(modifier.fillMaxSize().background(MiuixTheme.colorScheme.background))
    }
}
