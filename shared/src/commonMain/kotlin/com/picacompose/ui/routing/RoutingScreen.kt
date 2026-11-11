package com.picacompose.ui.routing

import com.picacompose.ui.components.PicaSurface
import com.picacompose.ui.components.PicaCircularProgress
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import com.picacompose.ui.theme.SkinTokens
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import com.picacompose.ui.components.PicaTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.picacompose.data.LineRouter
import com.picacompose.data.PicaLine
import com.picacompose.ui.components.PicaBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 冷启动第一屏：选线路。
 *
 * - [manual] = false（开着自动测速）：进来先测一遍，最快的默认选中，过一秒自动进。
 * - [manual] = true（关掉自动测速）：不测速、也不自动挑，就摆着几条线路等人点一条再进。
 */
@Composable
fun RoutingScreen(manual: Boolean = false, onReady: () -> Unit) {
    val controller = remember { RoutingController() }
    val scope = rememberCoroutineScope()
    var entering by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (manual) {
            // 手动模式只把线路和下发 IP 准备好，测速留给「测速一次」按钮。
            controller.runManual()
        } else {
            controller.run()
            delay(1_100)
            if (!entering) {
                entering = true
                onReady()
            }
        }
    }

    fun enter(line: PicaLine? = null) {
        if (entering) return
        entering = true
        scope.launch {
            if (line != null) {
                // 手动模式：进来时客户端还没建过，哪怕点的是上次那条也得建一次。
                if (manual) controller.chooseExplicit(line) else controller.choose(line)
                // 手动选的这条也要挂上故障兜底：之后连不上只在地址池里换，不再自己测线换线。
                if (manual) LineRouter.start(scope, line, controller.ips, probe = false)
            }
            onReady()
        }
    }

    Box(Modifier.fillMaxSize()) {
        PicaBackdrop()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp)) {
                Text(
                    text = "选一条线路",
                    style = MiuixTheme.textStyles.title2,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (manual) {
                        "自动测速关着，这里不会测速也不会自动挑。点哪条就用哪条，进主界面后才开始加载。"
                    } else {
                        "启动时会先拉一次线路列表，这里已经自动测好速，最快的那条默认选中，过一秒自动进去。"
                    },
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
                Spacer(Modifier.height(20.dp))

                controller.probes.forEach { probe ->
                    LineCard(
                        probe = probe,
                        chosen = controller.chosen == probe.line,
                        last = controller.lastLine == probe.line,
                        loading = controller.loading,
                        idle = manual,
                        onClick = { enter(probe.line) },
                    )
                    Spacer(Modifier.height(12.dp))
                }

                controller.note?.let { note ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = note,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (controller.ips.isEmpty()) {
                            "没有拿到下发 IP"
                        } else {
                            "${controller.ips.size} 个下发 IP"
                        },
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                    PicaTextButton(
                        onClick = {
                            scope.launch {
                                entering = false
                                controller.run()
                            }
                        },
                        enabled = !controller.loading,
                    ) {
                        Text(if (manual) "测速一次" else "重新测速")
                    }
                }
            }
        }
    }
}

@Composable
private fun LineCard(
    probe: LineProbe,
    chosen: Boolean,
    last: Boolean,
    loading: Boolean,
    /** 手动模式：还没测过的时候右边什么都别显示，别写成「测速中」。 */
    idle: Boolean,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    PicaSurface(
        shape = RoundedCornerShape(SkinTokens.rowRadius),
        color = if (chosen) colors.primaryContainer else colors.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !loading, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = probe.line.title,
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.SemiBold,
                        color = if (chosen) colors.onPrimaryContainer else colors.onSurface,
                    )
                    if (last) {
                        Spacer(Modifier.size(8.dp))
                        PicaSurface(
                            shape = RoundedCornerShape(SkinTokens.chipRadius),
                            color = colors.tertiaryContainer,
                        ) {
                            Text(
                                text = "上次",
                                style = MiuixTheme.textStyles.footnote2,
                                color = colors.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = probe.line.detail,
                    style = MiuixTheme.textStyles.footnote1,
                    color = if (chosen) colors.onPrimaryContainer.copy(alpha = 0.8f) else colors.onSurfaceSecondary,
                )
            }
            Spacer(Modifier.size(12.dp))
            when {
                idle && !probe.done -> Unit
                !probe.done -> Row(verticalAlignment = Alignment.CenterVertically) {
                    PicaCircularProgress(
color = colors.onSurfaceSecondary,
modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "测速中",
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceSecondary,
                    )
                }

                probe.latencyMs != null -> Text(
                    text = "${probe.latencyMs} ms",
                    style = MiuixTheme.textStyles.button,
                    color = if (chosen) colors.onPrimaryContainer else colors.primary,
                )

                else -> Text(
                    text = "不通",
                    style = MiuixTheme.textStyles.button,
                    color = colors.error,
                )
            }
        }
    }
}
