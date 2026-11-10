package com.picacompose.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.picacompose.data.ImageIpsStatus
import com.picacompose.data.LinePreferences
import com.picacompose.data.LineRouter
import com.picacompose.data.RouteStore
import com.picacompose.data.SessionStore
import com.picacompose.picaapi.ImageIps
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon

/** 设置：账号状态、线路开关、版本信息，最后是退出登录。 */
@Composable
fun SettingsContent(onSignOut: () -> Unit) {
    val session = remember { SessionStore() }
    val savedAccount = session.savedAccount
    val rememberOn = session.savedToken != null
    val route = remember { RouteStore() }
    val preferences = remember { LinePreferences() }
    val line = route.savedLine
    val latency = route.savedLatencyMs
    val ipCount = route.savedIps.size
    // 开关的界面状态：改完立刻落盘，下次冷启动按新的来。
    var autoProbe by remember { mutableStateOf(preferences.autoProbe) }
    var fastEntry by remember { mutableStateOf(preferences.fastEntry) }
    var doh by remember { mutableStateOf(preferences.doh) }
    var useImageIps by remember { mutableStateOf(preferences.imageIps) }
    // 「几分钟前」要自己走：没有系统时钟依赖，用单调时间算相对时长。
    val imageIpsAgo by produceState(-1L) {
        while (true) {
            value = ImageIpsStatus.finishedAt?.elapsedNow()?.inWholeSeconds ?: -1L
            delay(10_000)
        }
    }

    PageScaffold(
        header = { PageHeader(title = "设置") },
    ) {
        item {
            PicaCard {
                DetailRow(
                    icon = PicaIcon.Person,
                    title = "记住账号",
                    value = if (rememberOn) "已开启" else "未开启",
                )
                DetailRow(
                    icon = PicaIcon.Lock,
                    title = "保存的账号",
                    value = savedAccount ?: "还没有",
                )
                DetailRow(
                    icon = PicaIcon.Eye,
                    title = "密码",
                    value = "不落盘",
                )
            }
            Spacer(Modifier.height(14.dp))
        }
        item {
            PicaCard {
                SwitchRow(
                    icon = PicaIcon.Speed,
                    title = "自动测速",
                    summary = "启动时测一遍线路，挑最快的用；关掉就用上次的线路，不测也不自动换" +
                        "（当场就会停掉正在跑的测速，不用重启）。",
                    checked = autoProbe,
                    onCheckedChange = { on ->
                        autoProbe = on
                        preferences.autoProbe = on
                        // 关掉之后别再让后台测速跑完顺手把线路切了。
                        if (!on) LineRouter.stop()
                    },
                )
                // 子开关：父开关关掉时置灰，但值留着，父开关开回来还是原来的选择。
                SwitchRow(
                    icon = PicaIcon.Bolt,
                    title = "快速进入并后台测速",
                    summary = "先按上次的线路进主界面，测速在后台跑；关掉就停在线路选择页，等测完再进。",
                    checked = fastEntry,
                    enabled = autoProbe,
                    indent = true,
                    onCheckedChange = { on ->
                        fastEntry = on
                        preferences.fastEntry = on
                    },
                )
                Spacer(Modifier.height(6.dp))
                DetailRow(
                    icon = PicaIcon.ArrowRight,
                    title = "线路",
                    value = line?.let { "${it.title} · 通道 ${it.channel}" } ?: "还没选",
                )
                DetailRow(
                    icon = PicaIcon.Gear,
                    title = "测速",
                    value = when {
                        !autoProbe -> "自动测速已关"
                        latency != null -> "$latency ms"
                        ipCount > 0 -> "$ipCount 个下发 IP"
                        else -> "系统解析"
                    },
                )
                // 后台测速列表：换完线路会重测一遍，这里就是最近一次的结果，不通的明确标出来。
                LineRouter.statuses.forEach { status ->
                    DetailRow(
                        icon = PicaIcon.Bolt,
                        title = status.line.title,
                        value = status.latencyMs?.let { "$it ms" } ?: "不通",
                    )
                }
                Spacer(Modifier.height(4.dp))
                SwitchRow(
                    icon = PicaIcon.Shield,
                    title = "DoH 解析",
                    summary = "用 DNS over HTTPS 解析所有域名（接口、图片），绕开被污染的 DNS。" +
                        "默认关；打了之后每次启动多问一次 DoH（和 /init 并行，不会多等一个来回）。" +
                        "解析不通会自动关掉这个开关，不会让应用变成连不上。",
                    checked = doh,
                    onCheckedChange = { on ->
                        doh = on
                        preferences.doh = on
                    },
                )
                Spacer(Modifier.height(6.dp))
                SwitchRow(
                    icon = PicaIcon.Speed,
                    title = "图片 IP 优选",
                    summary = "后台把 Cloudflare 官方地址段取样测一遍，500ms 以内的按延迟取最好的 10 台，" +
                        "图片和接口轮着用；关掉只用 /init 下发的地址。开关立即生效，不用重启。",
                    checked = useImageIps,
                    onCheckedChange = { on ->
                        useImageIps = on
                        preferences.imageIps = on
                        ImageIps.updateEnabled(on)
                        if (!on) {
                            ImageIps.preferOfficialFirst()
                            ImageIpsStatus.off()
                        }
                    },
                )
                DetailRow(
                    icon = PicaIcon.Gear,
                    title = "优选状态",
                    value = imageIpsPhaseText(),
                )
                DetailRow(
                    icon = PicaIcon.Bolt,
                    title = "地址池",
                    value = imageIpsPoolText(),
                )
                DetailRow(
                    icon = PicaIcon.Speed,
                    title = "上次完成",
                    value = imageIpsAgoText(imageIpsAgo),
                )
            }
            Spacer(Modifier.height(14.dp))
        }
        item {
            PicaCard {
                DetailRow(PicaIcon.Grid, "主题", "Material You 基线配色")
                DetailRow(PicaIcon.Gear, "版本", "0.1.0")
                DetailRow(PicaIcon.ArrowRight, "接口", "picaapi 模块")
            }
            Spacer(Modifier.height(14.dp))
        }
        item {
            Text(
                text = "记住账号只保存 token 和账号名，下次打开直接恢复会话；" +
                    "退出登录会清掉 token，账号名留着方便下次登录。线路的开关改完下次打开生效：" +
                    "自动测速关掉后不会再有后台测速，也不会自己换线路，网络变了要手动去线路页重选；" +
                    "快速进入关掉后每次启动会多等一次测速；DoH 打开后接口走 DoH 解析（图片另说，见下），" +
                    "只有一个可信地址都拿不到时会自动关掉开关并退回系统 DNS（设置页的开关也会变回关），" +
                    "单个端点挂掉会在几路端点之间自动换到还能用的那一路。" +
                    "图片地址优先用 /init 下发的图床；图床域名（storage1 / storage-b / s3.picacomic.com）" +
                    "在很多网络上会被系统解析成别的公司的 IP，所以图片请求单独钉了一组 Cloudflare 地址，" +
                    "系统解析的结果排在后面兜底。启动后会在后台把候选地址挨个测一遍，按握手耗时挑最快的三台，" +
                    "测速范围是 Cloudflare 官方公布的整套地址段（均匀取样几百台一起测），500ms 以内按延迟取最好的 10 台，" +
                    "之后每 30 分钟重测；冷启动时先用 /init 下发的官方源，优选测完再顶到前面。" +
                    "接口域名也在 Cloudflare 后面，所以同一份顺序也管接口 —— 优选出来的地址排前面，" +
                    "线路钉住的 IP 跟着兜底。候选里带 IPv6 地址，设备有 v6 出口就会一起测、一起用；" +
                    "全测不出来就退回官方池和内置种子地址，不会把能用的清空。" +
                    "上面的「图片 IP 优选」可以整个关掉：关了就不再后台测速，只用 /init 下发的地址加内置种子。" +
                    "只要手上有优选池，就不会再自动测线路、也不会自己换线路；请求连不上时先在池子里换一台地址，" +
                    "连着换三台都不行才重新测一遍线路。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
        }
        item {
            OutlinedButton(
                onClick = onSignOut,
                shape = CircleShape,
                modifier = Modifier.height(52.dp).fillMaxWidth(),
            ) {
                PicaGlyph(PicaIcon.Logout, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("退出登录", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** 后台优选现在的状态。 */
private fun imageIpsPhaseText(): String = when (ImageIpsStatus.phase) {
    ImageIpsStatus.Phase.Off -> "已关闭"
    ImageIpsStatus.Phase.Idle -> "准备中"
    ImageIpsStatus.Phase.Scanning -> "测速中 ${ImageIpsStatus.tested}/${ImageIpsStatus.total}"
    ImageIpsStatus.Phase.Done -> "已完成"
    ImageIpsStatus.Phase.Failed -> "这轮没测出可用地址"
}

/** 池子里现在有什么：优选结果、候选数量，或者退回官方源时的说明。 */
private fun imageIpsPoolText(): String = when {
    ImageIpsStatus.phase == ImageIpsStatus.Phase.Off -> "只用 /init 下发的地址"
    ImageIpsStatus.phase == ImageIpsStatus.Phase.Scanning && ImageIpsStatus.pool.isNotEmpty() ->
        "先用着 ${ImageIpsStatus.pool.size} 台 · ${ImageIpsStatus.fastestMs ?: 0}ms"
    ImageIpsStatus.pool.isEmpty() -> "官方源 + 内置种子"
    else -> ImageIpsStatus.fastestMs
        ?.let { "${it}ms 最快 · ${ImageIpsStatus.pool.size} 台" }
        ?: "${ImageIpsStatus.pool.size} 台"
}

/** 上一轮是什么时候测完的。 */
private fun imageIpsAgoText(seconds: Long): String = when {
    seconds < 0 -> "还没有测过"
    seconds < 60 -> "刚刚"
    seconds < 3600 -> "${seconds / 60} 分钟前"
    else -> "${seconds / 3600} 小时前"
}

/** 设置页的一行开关：左边图标 + 标题 + 说明，右边 Material 3 的 Switch。 */
@Composable
private fun SwitchRow(
    icon: PicaIcon,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    /** 子开关往右缩一点，视觉上挂在上面那个开关下面。 */
    indent: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val alpha = if (enabled) 1f else 0.45f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indent) 22.dp else 0.dp)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PicaGlyph(
            icon = icon,
            modifier = Modifier.size(18.dp),
            tint = colors.onSurfaceVariant.copy(alpha = alpha),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface.copy(alpha = alpha),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}
