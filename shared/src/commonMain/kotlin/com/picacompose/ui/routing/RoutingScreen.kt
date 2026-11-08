package com.picacompose.ui.routing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.picacompose.data.PicaLine
import com.picacompose.ui.components.PicaBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 冷启动第一屏：自动测速选线路。
 *
 * 选完停 1 秒多再自动进去，留一点时间让人看清选了哪条；想换就点别的卡片，点了立刻进。
 */
@Composable
fun RoutingScreen(onReady: () -> Unit) {
    val controller = remember { RoutingController() }
    val scope = rememberCoroutineScope()
    var entering by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        controller.run()
        delay(1_100)
        if (!entering) {
            entering = true
            onReady()
        }
    }

    fun enter(line: PicaLine? = null) {
        if (entering) return
        entering = true
        scope.launch {
            if (line != null) controller.choose(line)
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
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "启动时会先拉一次线路列表，这里已经自动测好速，最快的那条默认选中，过一秒自动进去。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))

                controller.probes.forEach { probe ->
                    LineCard(
                        probe = probe,
                        chosen = controller.chosen == probe.line,
                        last = controller.lastLine == probe.line,
                        loading = controller.loading,
                        onClick = { enter(probe.line) },
                    )
                    Spacer(Modifier.height(12.dp))
                }

                controller.note?.let { note ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            scope.launch {
                                entering = false
                                controller.run()
                            }
                        },
                        enabled = !controller.loading,
                    ) {
                        Text("重新测速")
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
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (chosen) colors.primaryContainer else colors.surface,
        tonalElevation = if (chosen) 0.dp else 1.dp,
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
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (chosen) colors.onPrimaryContainer else colors.onSurface,
                    )
                    if (last) {
                        Spacer(Modifier.size(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = colors.surfaceVariant,
                        ) {
                            Text(
                                text = "上次",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = probe.line.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (chosen) colors.onPrimaryContainer.copy(alpha = 0.8f) else colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(12.dp))
            when {
                !probe.done -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "测速中",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }

                probe.latencyMs != null -> Text(
                    text = "${probe.latencyMs} ms",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (chosen) colors.onPrimaryContainer else colors.primary,
                )

                else -> Text(
                    text = "不通",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.error,
                )
            }
        }
    }
}
