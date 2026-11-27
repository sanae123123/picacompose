package com.picacompose.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.picacompose.data.ImageHosts
import com.picacompose.data.ImageIpsStatus
import com.picacompose.data.LinePreferences
import com.picacompose.data.PicaLine
import com.picacompose.data.RouteStore
import com.picacompose.picaapi.ImageIps
import com.picacompose.picaapi.ImageQuality
import com.picacompose.picaapi.NetworkLog
import com.picacompose.picaapi.resolveViaHttpDns
import com.picacompose.ui.components.PicaBackdrop
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.PicaIconButton
import com.picacompose.ui.components.PicaOutlinedButton
import com.picacompose.ui.components.PicaToast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.time.TimeSource

/**
 * 网络链路调试页：把线路、图床、几层 IP 池的实际取值摊开，出问题时不用连电脑看日志。
 *
 * 从设置里点「网络链路」进来，整屏盖在上面。所有数字都是每秒钟重新读一次的实时值 ——
 * 后台优选跑一轮、HttpDNS 换了地址、开关拨了一下，这里都会跟着变。
 *
 * 页尾的「复制」把整页内容拼成一段文本塞进剪贴板，反馈问题时直接粘过来就行。
 */
@Composable
fun NetworkDebugScreen(onDismiss: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val route = remember { RouteStore() }
    val preferences = remember { LinePreferences() }

    var pool by remember { mutableStateOf(readPoolSnapshot()) }
    var logs by remember { mutableStateOf(NetworkLog.snapshot()) }
    var probing by remember { mutableStateOf(false) }
    var probeReport by remember { mutableStateOf("") }

    // 池子里的字段不是 Compose 状态（后台线程随时改），只能定时重读一份快照。
    LaunchedEffect(Unit) {
        while (true) {
            pool = readPoolSnapshot()
            logs = NetworkLog.snapshot()
            delay(1_000)
        }
    }

    val line = route.savedLine
    val latency = route.savedLatencyMs
    val pinned = route.savedIps
    val quality = ImageQuality.fromIndex(preferences.imageQuality)
    val phase = when (ImageIpsStatus.phase) {
        ImageIpsStatus.Phase.Idle -> "还没开始"
        ImageIpsStatus.Phase.Scanning -> "测量中 ${ImageIpsStatus.tested}/${ImageIpsStatus.total}"
        ImageIpsStatus.Phase.Done -> "测完，留了 ${ImageIpsStatus.pool.size} 个"
        ImageIpsStatus.Phase.Failed -> "一轮都没测出来"
        ImageIpsStatus.Phase.Off -> "已关闭"
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
                        PageHeader(title = "网络链路")
                    }
                },
            ) {
                item {
                    PicaCard {
                        DetailRow(
                            icon = PicaIcon.Speed,
                            title = "线路",
                            value = line?.let { "${it.title} · 通道 ${it.channel}" } ?: "还没选",
                        )
                        DetailRow(
                            icon = PicaIcon.Gear,
                            title = "测速",
                            value = latency?.let { "$it ms" } ?: "还没测",
                        )
                        DetailRow(
                            icon = PicaIcon.Bolt,
                            title = "自动测速",
                            value = if (preferences.autoProbe) "开" else "关",
                        )
                        DetailRow(
                            icon = PicaIcon.ArrowRight,
                            title = "快速进入",
                            value = if (preferences.fastEntry) "开" else "关",
                        )
                        DetailRow(
                            icon = PicaIcon.Lock,
                            title = "钉住的接口 IP",
                            value = if (pinned.isEmpty()) "没有" else "${pinned.size} 个",
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                }
                item {
                    PicaCard {
                        DetailRow(
                            icon = PicaIcon.Star,
                            title = "图床",
                            value = ImageHosts.server ?: "未下发",
                        )
                        DetailRow(
                            icon = PicaIcon.Filter,
                            title = "图片质量",
                            value = when (quality) {
                                ImageQuality.Original -> "原图"
                                ImageQuality.Low -> "低"
                                ImageQuality.Medium -> "中"
                                ImageQuality.High -> "高"
                            },
                        )
                        DetailRow(
                            icon = PicaIcon.Speed,
                            title = "IP 优选",
                            value = if (preferences.imageIps) "开" else "关",
                        )
                        DetailRow(
                            icon = PicaIcon.Notice,
                            title = "优选状态",
                            value = phase,
                        )
                        DetailRow(
                            icon = PicaIcon.Lock,
                            title = "DoH",
                            value = if (preferences.doh) "开" else "关",
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                }
                item {
                    PicaCard {
                        PoolBlock("官方 HttpDNS", pool.httpDns)
                        PoolBlock("本地优选", pool.preferred)
                        PoolBlock("/init 下发", pool.official)
                        PoolBlock("内置种子", pool.seeds)
                        Spacer(Modifier.height(12.dp))
                        PicaOutlinedButton(
                            onClick = {
                                if (probing) return@PicaOutlinedButton
                                probing = true
                                scope.launch {
                                    val started = TimeSource.Monotonic.markNow()
                                    val ips = runCatching { resolveViaHttpDns("img.picacomic.com") }
                                        .getOrDefault(emptyList())
                                    val ms = started.elapsedNow().inWholeMilliseconds
                                    if (ips.isNotEmpty()) ImageIps.updateHttpDns(ips)
                                    probeReport = if (ips.isEmpty()) {
                                        "没拿到地址（${ms} ms）"
                                    } else {
                                        "拿到 ${ips.size} 个（${ms} ms）"
                                    }
                                    pool = readPoolSnapshot()
                                    probing = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                        ) {
                            Text(
                                text = if (probing) "查询中…" else "重新查询官方 HttpDNS",
                                style = MiuixTheme.textStyles.button,
                            )
                        }
                        if (probeReport.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = probeReport,
                                style = MiuixTheme.textStyles.footnote1,
                                color = colors.onSurfaceSecondary,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                item {
                    PicaCard {
                        Text(
                            text = "当前生效顺序（前 10）",
                            style = MiuixTheme.textStyles.body1,
                            color = colors.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = pool.ordered.take(10)
                                .mapIndexed { index, ip -> "${index + 1}. $ip" }
                                .joinToString("\n")
                                .ifEmpty { "空" },
                            style = MiuixTheme.textStyles.footnote1,
                            color = colors.onSurfaceSecondary,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                }
                item {
                    PicaCard {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "最近连接",
                                style = MiuixTheme.textStyles.body1,
                                color = colors.onSurface,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "${logs.size} 条",
                                style = MiuixTheme.textStyles.footnote1,
                                color = colors.onSurfaceSecondary,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = logs.take(20).joinToString("\n").ifEmpty { "还没有记录" },
                            style = MiuixTheme.textStyles.footnote1,
                            color = colors.onSurfaceSecondary,
                        )
                        Spacer(Modifier.height(12.dp))
                        PicaOutlinedButton(
                            onClick = {
                                NetworkLog.clear()
                                logs = NetworkLog.snapshot()
                            },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                        ) {
                            Text("清空日志", style = MiuixTheme.textStyles.button)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                item {
                    PicaOutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(pool.report(line, latency, pinned, logs)))
                            PicaToast.show("已复制到剪贴板")
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                    ) {
                        Text("复制全部", style = MiuixTheme.textStyles.button)
                    }
                }
            }
        }
    }
}

/** 几层地址池的一次快照；`httpDns` 等字段不是 Compose 状态，得自己定期重读。 */
private data class PoolSnapshot(
    val httpDns: List<String> = emptyList(),
    val preferred: List<String> = emptyList(),
    val official: List<String> = emptyList(),
    val seeds: List<String> = emptyList(),
    val ordered: List<String> = emptyList(),
) {

    /** 拼成一段能直接贴出来的文本：「复制全部」用它。 */
    fun report(
        line: PicaLine?,
        latency: Long?,
        pinned: List<String>,
        logs: List<NetworkLog.Entry>,
    ): String = buildString {
        appendLine("网络链路")
        appendLine("线路：${line?.let { "${it.title} · 通道 ${it.channel}" } ?: "还没选"}")
        appendLine("测速：${latency?.let { "$it ms" } ?: "还没测"}")
        appendLine("钉住的接口 IP：${pinned.size} 个 ${pinned.joinToString(",")}")
        appendLine("图床：${ImageHosts.server ?: "未下发"}")
        appendLine("官方 HttpDNS（${httpDns.size}）：${httpDns.joinToString(",")}")
        appendLine("本地优选（${preferred.size}）：${preferred.joinToString(",")}")
        appendLine("/init 下发（${official.size}）：${official.joinToString(",")}")
        appendLine("内置种子（${seeds.size}）：${seeds.joinToString(",")}")
        append("生效顺序：${ordered.take(10).joinToString(",")}")
        appendLine()
        appendLine()
        appendLine("最近连接（新→旧）：")
        logs.take(20).forEach { appendLine(it.toString()) }
    }
}

private fun readPoolSnapshot(): PoolSnapshot = PoolSnapshot(
    httpDns = ImageIps.httpDns,
    preferred = ImageIps.preferred,
    official = ImageIps.official,
    seeds = ImageIps.seeds,
    ordered = ImageIps.ordered(),
)

/** 一层地址池：标题 + 个数 + 地址本身（空格隔开，放不下自己换行）。 */
@Composable
private fun PoolBlock(title: String, ips: List<String>) {
    val colors = MiuixTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                color = colors.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${ips.size} 个",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = ips.joinToString("  ").ifEmpty { "空" },
            style = MiuixTheme.textStyles.footnote1,
            color = colors.onSurfaceSecondary,
        )
    }
}
