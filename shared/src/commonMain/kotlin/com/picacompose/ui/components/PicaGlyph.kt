package com.picacompose.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 手绘的小图标。
 *
 * 只有登录页需要四五个图标，为它们引入 material-icons-extended（体积大、且在
 * Compose Multiplatform 里的 artifact 名一直在改）不划算，所以直接 Canvas 画。
 */
enum class PicaIcon { Mail, Lock, Person, Eye, EyeOff, ArrowRight, Logout }

@Composable
fun PicaGlyph(
    icon: PicaIcon,
    modifier: Modifier = Modifier.size(20.dp),
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier) {
        val s = size.minDimension
        val stroke = Stroke(
            width = s * 0.085f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )

        when (icon) {
            PicaIcon.Mail -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(s * 0.10f, s * 0.22f),
                    size = Size(s * 0.80f, s * 0.56f),
                    cornerRadius = CornerRadius(s * 0.14f),
                    style = stroke,
                )
                val flap = Path().apply {
                    moveTo(s * 0.17f, s * 0.30f)
                    lineTo(s * 0.50f, s * 0.57f)
                    lineTo(s * 0.83f, s * 0.30f)
                }
                drawPath(flap, tint, style = stroke)
            }

            PicaIcon.Lock -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(s * 0.22f, s * 0.45f),
                    size = Size(s * 0.56f, s * 0.42f),
                    cornerRadius = CornerRadius(s * 0.11f),
                    style = stroke,
                )
                drawArc(
                    color = tint,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(s * 0.32f, s * 0.16f),
                    size = Size(s * 0.36f, s * 0.36f),
                    style = stroke,
                )
            }

            PicaIcon.Person -> {
                drawCircle(
                    color = tint,
                    radius = s * 0.145f,
                    center = Offset(s * 0.5f, s * 0.31f),
                    style = stroke,
                )
                val shoulders = Path().apply {
                    moveTo(s * 0.18f, s * 0.86f)
                    quadraticBezierTo(s * 0.5f, s * 0.53f, s * 0.82f, s * 0.86f)
                }
                drawPath(shoulders, tint, style = stroke)
            }

            PicaIcon.Eye, PicaIcon.EyeOff -> {
                val eye = Path().apply {
                    moveTo(s * 0.08f, s * 0.5f)
                    quadraticBezierTo(s * 0.5f, s * 0.10f, s * 0.92f, s * 0.5f)
                    quadraticBezierTo(s * 0.5f, s * 0.90f, s * 0.08f, s * 0.5f)
                    close()
                }
                drawPath(eye, tint, style = stroke)
                drawCircle(
                    color = tint,
                    radius = s * 0.10f,
                    center = Offset(s * 0.5f, s * 0.5f),
                )
                if (icon == PicaIcon.EyeOff) {
                    drawLine(
                        color = tint,
                        start = Offset(s * 0.16f, s * 0.16f),
                        end = Offset(s * 0.84f, s * 0.84f),
                        strokeWidth = s * 0.085f,
                        cap = StrokeCap.Round,
                    )
                }
            }

            PicaIcon.ArrowRight -> {
                drawLine(
                    color = tint,
                    start = Offset(s * 0.12f, s * 0.5f),
                    end = Offset(s * 0.86f, s * 0.5f),
                    strokeWidth = s * 0.085f,
                    cap = StrokeCap.Round,
                )
                val chevron = Path().apply {
                    moveTo(s * 0.58f, s * 0.22f)
                    lineTo(s * 0.88f, s * 0.5f)
                    lineTo(s * 0.58f, s * 0.78f)
                }
                drawPath(chevron, tint, style = stroke)
            }

            PicaIcon.Logout -> {
                val door = Path().apply {
                    moveTo(s * 0.44f, s * 0.12f)
                    lineTo(s * 0.14f, s * 0.12f)
                    lineTo(s * 0.14f, s * 0.88f)
                    lineTo(s * 0.44f, s * 0.88f)
                }
                drawPath(door, tint, style = stroke)
                drawLine(
                    color = tint,
                    start = Offset(s * 0.40f, s * 0.5f),
                    end = Offset(s * 0.88f, s * 0.5f),
                    strokeWidth = s * 0.085f,
                    cap = StrokeCap.Round,
                )
                val chevron = Path().apply {
                    moveTo(s * 0.66f, s * 0.28f)
                    lineTo(s * 0.90f, s * 0.5f)
                    lineTo(s * 0.66f, s * 0.72f)
                }
                drawPath(chevron, tint, style = stroke)
            }
        }
    }
}
