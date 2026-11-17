package com.picacompose.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.picacompose.data.APP_VERSION
import com.picacompose.data.REPO_URL
import com.picacompose.ui.components.PicaBackdrop
import com.picacompose.ui.components.PicaButton
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.PicaIconButton
import com.picacompose.ui.components.rememberAppIcon
import com.picacompose.ui.components.rememberBrowserOpener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 关于页：图标、彩蛋、开发名单和几句话介绍。
 *
 * 从设置里点「关于」进来，整屏盖在上面；点左上角或按返回键关掉。
 * 图标可以戳：每戳一下弹一下，还会从屏幕上边掉一颗星星下来，在页面里一直弹来弹去；
 * 点得越多星星越多，都不会消失。
 */
@Composable
fun AboutScreen(onDismiss: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    val openBrowser = rememberBrowserOpener()
    // 点一下多一颗，星星在页面里一直弹，不消失。
    var stars by remember { mutableStateOf<List<Star>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val bounce = remember { Animatable(1f) }

    fun poke() {
        stars = stars + Star.spawn()
        scope.launch {
            bounce.snapTo(0.86f)
            bounce.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioHighBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    // 自己推帧：每颗星按速度走一步，撞到四边就反弹。
    LaunchedEffect(Unit) {
        var lastFrame = 0L
        while (true) {
            // 没有星星的时候不用逐帧唤醒，等一下再看。
            if (stars.isEmpty()) {
                lastFrame = 0L
                delay(60)
                continue
            }
            withFrameNanos { now ->
                if (lastFrame == 0L) lastFrame = now
                val dt = ((now - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastFrame = now
                if (canvasSize.width > 0 && canvasSize.height > 0) {
                    stars = stars.map {
                        it.step(dt, canvasSize.width.toFloat(), canvasSize.height.toFloat())
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.background),
        ) {
            PicaBackdrop()
            PageScaffold(
                header = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PicaIconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                            PicaGlyph(
                                icon = PicaIcon.Back,
                                modifier = Modifier.size(22.dp),
                                tint = colors.onBackground,
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        PageHeader(title = "关于")
                    }
                },
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppIcon(scale = bounce.value, onPoke = ::poke)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "❤️lyx致最可爱的zqs妹妹❤️",
                            style = MiuixTheme.textStyles.title4,
                            color = colors.onBackground,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "开发：auayy~（zqs）、亿只鱼（ljl）、gskhaa（lyx）、CoooC（zsw）",
                            style = MiuixTheme.textStyles.footnote1,
                            color = colors.onSurfaceSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(18.dp))
                    PicaCard {
                        Text(
                            text = "这是什么",
                            style = MiuixTheme.textStyles.title4,
                            color = colors.onBackground,
                        )
                        Spacer(Modifier.height(10.dp))
                        Paragraph("PicACG 的第三方客户端，用 Kotlin + Compose Multiplatform 写的一套界面，Android、iOS 和桌面共用同一份代码。")
                        Paragraph("登录同一个 PicACG 账号，列表、排行榜、搜索、收藏、阅读历史和评论区都能用，留言板和楼中楼回复也在。")
                        Paragraph("阅读进度会记住，看完一话自动接下一话；图片有 IP 优选和 DoH 兜底，加载更快也更稳。")
                        Paragraph("皮肤、只看无 H、自动更新这些开关都在设置里；应用本身不收集任何数据。")
                    }
                }
                item {
                    Spacer(Modifier.height(14.dp))
                    PicaCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "版本",
                                style = MiuixTheme.textStyles.body1,
                                color = colors.onSurface,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = APP_VERSION,
                                style = MiuixTheme.textStyles.footnote1,
                                color = colors.onSurfaceSecondary,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        PicaButton(
                            onClick = { openBrowser(REPO_URL) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text("打开 GitHub 项目", style = MiuixTheme.textStyles.button)
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "谢谢每一个愿意点开设置的人。",
                        style = MiuixTheme.textStyles.footnote2,
                        color = colors.onSurfaceSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            StarsOverlay(stars = stars, color = colors.primary) { canvasSize = it }
        }
    }
}

/** 应用图标本体：点一下弹一下，桌面 / iOS 取不到安装图标时退回「P」字标。 */
@Composable
private fun AppIcon(scale: Float, onPoke: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val appIcon = rememberAppIcon()
    Box(
        modifier = Modifier
            .size(88.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onPoke),
        contentAlignment = Alignment.Center,
    ) {
        if (appIcon != null) {
            Image(
                bitmap = appIcon,
                contentDescription = "PicaCompose 图标",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(colors.primary, colors.tertiaryContainer))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "P",
                    style = MiuixTheme.textStyles.title1,
                    color = colors.onPrimary,
                )
            }
        }
    }
}

/** 介绍里的一段字，段间留一点空。 */
@Composable
private fun Paragraph(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceSecondary,
    )
    Spacer(Modifier.height(8.dp))
}

/** 点图标后从屏幕上边掉下来的一颗星；在页面里到处弹，不会消失。 */
private data class Star(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val size: Float,
    val angle: Float,
    val spin: Float,
) {
    /** 按 dt 走一步，撞到四边各自反弹。位置按宽 / 高各自归一化。 */
    fun step(dt: Float, width: Float, height: Float): Star {
        var nx = x + vx * dt
        var ny = y + vy * dt
        var nvx = vx
        var nvy = vy
        val radiusPx = size * min(width, height)
        val rx = (radiusPx / width).coerceAtMost(0.4f)
        val ry = (radiusPx / height).coerceAtMost(0.4f)
        if (nx < rx && nvx < 0f) {
            nx = rx
            nvx = -nvx
        }
        if (nx > 1f - rx && nvx > 0f) {
            nx = 1f - rx
            nvx = -nvx
        }
        if (ny < ry && nvy < 0f) {
            ny = ry
            nvy = -nvy
        }
        if (ny > 1f - ry && nvy > 0f) {
            ny = 1f - ry
            nvy = -nvy
        }
        return copy(x = nx, y = ny, vx = nvx, vy = nvy, angle = (angle + spin * dt) % 360f)
    }

    companion object {
        /** 从屏幕上边外面冒出来，斜着往下掉。 */
        fun spawn(): Star = Star(
            x = 0.12f + Random.nextFloat() * 0.76f,
            y = -0.1f - Random.nextFloat() * 0.15f,
            vx = (Random.nextFloat() - 0.5f) * 0.22f,
            vy = 0.22f + Random.nextFloat() * 0.2f,
            size = 0.055f + Random.nextFloat() * 0.055f,
            angle = Random.nextFloat() * 360f,
            spin = (Random.nextFloat() - 0.5f) * 220f,
        )
    }
}

/** 星星覆盖层：铺在页面最上面，不拦触摸。 */
@Composable
private fun StarsOverlay(stars: List<Star>, color: Color, onSize: (IntSize) -> Unit) {
    Canvas(
        Modifier
            .fillMaxSize()
            .onSizeChanged(onSize),
    ) {
        stars.forEach { star ->
            val side = star.size * size.minDimension
            val cx = star.x * size.width
            val cy = star.y * size.height
            rotate(degrees = star.angle, pivot = Offset(cx, cy)) {
                translate(left = cx - side / 2f, top = cy - side / 2f) {
                    drawPath(starPath(side), color.copy(alpha = 0.92f))
                }
            }
        }
    }
}

/** 五角星，size × size，左上角为原点。 */
private fun starPath(size: Float): Path = Path().apply {
    val outer = size / 2f
    val inner = outer * 0.44f
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) outer else inner
        val angle = (-PI / 2 + i * PI / 5).toFloat()
        val px = outer + radius * cos(angle)
        val py = outer + radius * sin(angle)
        if (i == 0) moveTo(px, py) else lineTo(px, py)
    }
    close()
}
