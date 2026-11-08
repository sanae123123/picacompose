package com.picacompose.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.picacompose.data.pica
import com.picacompose.picaapi.model.UserProfile
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import kotlinx.coroutines.launch

/** 我的：资料明细 + 签到 + 退出登录。 */
@Composable
fun MineContent(profile: UserProfile, onSignOut: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    // 签到成功后本地的 isPunched 会立刻翻，不等下次拉 profile。
    var checkedIn by remember(profile) { mutableStateOf(profile.isPunched) }
    var punching by remember { mutableStateOf(false) }
    var punchError by remember { mutableStateOf<String?>(null) }

    PageScaffold {
        item {
            PageHeader(title = "我的", subtitle = "当前登录的账号。")
            Spacer(Modifier.height(18.dp))
        }
        item {
            PicaCard {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.primaryContainer,
                    modifier = Modifier.size(72.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = profile.name.take(1).ifBlank { "P" },
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = profile.name.ifBlank { "已登录" },
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                val title = profile.title
                if (!title.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            }
            Spacer(Modifier.height(14.dp))
        }
        item {
            PicaCard {
                DetailRow(PicaIcon.Person, "邮箱", profile.email ?: "未填")
                DetailRow(PicaIcon.Grid, "等级 / 经验", "Lv.${profile.level} · ${profile.exp}")
                DetailRow(PicaIcon.Home, "注册时间", profile.createdAt?.take(10) ?: "不知道")
                DetailRow(PicaIcon.Gear, "今日签到", if (checkedIn) "已签到" else "未签到")
            }
            Spacer(Modifier.height(14.dp))
        }
        if (!checkedIn) {
            item {
                Column(Modifier.fillMaxWidth()) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                punching = true
                                punchError = null
                                try {
                                    pica.users.punchIn()
                                    checkedIn = true
                                } catch (e: Exception) {
                                    // 可能是「今天已经签过了」而本地状态过期，问一次服务器再决定。
                                    val fresh = runCatching { pica.users.profile() }.getOrNull()
                                    if (fresh?.isPunched == true) {
                                        checkedIn = true
                                    } else {
                                        punchError = e.message ?: "签到失败，稍后再试"
                                    }
                                } finally {
                                    punching = false
                                }
                            }
                        },
                        enabled = !punching,
                        shape = CircleShape,
                        modifier = Modifier.height(52.dp).fillMaxWidth(),
                    ) {
                        if (punching) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            text = if (punching) "签到中…" else "签到",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    val error = punchError
                    if (error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.error,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
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
