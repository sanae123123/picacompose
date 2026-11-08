package com.picacompose.ui.home

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.picacompose.data.RouteStore
import com.picacompose.data.SessionStore
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon

/** 设置：先把当前的几个状态摆出来，开关等做完了再加。 */
@Composable
fun SettingsContent(onSignOut: () -> Unit) {
    val session = remember { SessionStore() }
    val savedAccount = session.savedAccount
    val rememberOn = session.savedToken != null
    val route = remember { RouteStore() }
    val line = route.savedLine
    val latency = route.savedLatencyMs
    val ipCount = route.savedIps.size

    PageScaffold {
        item {
            PageHeader(title = "设置", subtitle = "选项还少，先把状态说清楚。")
            Spacer(Modifier.height(18.dp))
        }
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
                    "退出登录会清掉 token，账号名留着方便下次登录。线路每次冷启动都会重新测速，" +
                    "换网络后不用手动改。",
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
