package com.picacompose.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 关于页：图标、彩蛋、开发名单和几句话介绍。
 *
 * 从设置里点「关于」进来，整屏盖在上面；点左上角或按返回键关掉。
 * 图标可以戳：每戳一下弹一下，还会从图标四周散出一圈小心心，并换一句彩蛋话。
 */
@Composable
fun AboutScreen(onDismiss: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    val openBrowser = rememberBrowserOpener()
    var pokes by remember { mutableIntStateOf(0) }
    var burstKey by remember { mutableIntStateOf(0) }
    val burst = remember { Animatable(0f) }
    val bounce = remember { Animatable(1f) }

    fun poke() {
        pokes += 1
        burstKey += 1
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

    LaunchedEffect(burstKey) {
        if (burstKey == 0) return@LaunchedEffect
        burst.snapTo(0f)
        burst.animateTo(1f, tween(durationMillis = 1800, easing = LinearEasing))
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
                        Box(Modifier.size(176.dp), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.matchParentSize()) {
                                drawHeartBurst(burst.value, colors.primary)
                            }
                            AppIcon(scale = bounce.value, onPoke = ::poke)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "❤️lyx致最可爱的zqs妹妹❤️",
                            style = MiuixTheme.textStyles.title4,
                            color = colors.onBackground,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "开发：auayy~（zsq）、亿只鱼（ljl）、gskhaa（lyx）、CoooC（zsw）",
                            style = MiuixTheme.textStyles.footnote1,
                            color = colors.onSurfaceSecondary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        AnimatedVisibility(
                            visible = pokes > 0,
                            enter = fadeIn(tween(200)),
                            exit = fadeOut(tween(120)),
                        ) {
                            Text(
                                text = EASTER_EGGS[(pokes - 1).coerceAtLeast(0) % EASTER_EGGS.size],
                                style = MiuixTheme.textStyles.footnote2,
                                color = colors.primary,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Text(
                            text = "小提示：戳一下图标",
                            style = MiuixTheme.textStyles.footnote2,
                            color = colors.onSurfaceSecondary.copy(alpha = 0.7f),
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

/** 点图标时从中心散出去的一圈小心心：进度 0 → 1，越散越远、越淡。 */
private fun DrawScope.drawHeartBurst(progress: Float, color: Color) {
    if (progress <= 0f || progress >= 1f) return
    val count = 8
    val maxRadius = size.minDimension * 0.42f
    repeat(count) { index ->
        val angle = (2.0 * PI * index / count + 0.4).toFloat()
        val radius = maxRadius * (0.30f + 0.70f * progress)
        val heartSize = size.minDimension * 0.14f * (1f - 0.35f * progress)
        val cx = center.x + radius * cos(angle)
        val cy = center.y + radius * sin(angle)
        translate(left = cx - heartSize / 2f, top = cy - heartSize / 2f) {
            drawPath(heartPath(heartSize), color.copy(alpha = (1f - progress) * 0.85f))
        }
    }
}

/** 一颗 size × size 的心，左上角为原点。 */
private fun heartPath(size: Float): Path = Path().apply {
    moveTo(size * 0.5f, size * 0.94f)
    cubicTo(size * 0.04f, size * 0.62f, size * 0.10f, size * 0.16f, size * 0.5f, size * 0.36f)
    cubicTo(size * 0.90f, size * 0.16f, size * 0.96f, size * 0.62f, size * 0.5f, size * 0.94f)
    close()
}

/** 戳图标时轮着蹦出来的话。 */
private val EASTER_EGGS = listOf(
    "彩蛋：lyx 说，zqs 妹妹要天天开心 ❤️",
    "被你点到了，图标害羞地跳了一下",
    "auayy~：再点要收费了（开玩笑的）",
    "亿只鱼路过：看到彩蛋就算自己人了",
    "gskhaa：zqs 妹妹天下第一可爱",
    "CoooC：这个彩蛋是大家一起想的",
)
