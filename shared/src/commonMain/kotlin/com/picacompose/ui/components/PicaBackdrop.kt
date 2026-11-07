package com.picacompose.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 页面底色：从 primaryContainer 淡到 background，再叠两团柔光。
 *
 * Material You 的「表面有层次」靠的就是这种大面积低饱和色块，比纯色背景好看很多，
 * 而且深色模式下自动跟着换。
 */
@Composable
fun PicaBackdrop(modifier: Modifier = Modifier) {
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
            )
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
}
