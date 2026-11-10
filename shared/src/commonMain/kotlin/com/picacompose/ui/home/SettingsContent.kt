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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.picacompose.data.LinePreferences
import com.picacompose.data.LineRouter
import com.picacompose.data.RouteStore
import com.picacompose.data.SessionStore
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
                    summary = "启动时测一遍线路，挑最快的用；关掉就用上次的线路，不测也不自动换。",
                    checked = autoProbe,
                    onCheckedChange = { on ->
                        autoProbe = on
                        preferences.autoProbe = on
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
                    "系统解析的结果排在后面兜底。",
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
