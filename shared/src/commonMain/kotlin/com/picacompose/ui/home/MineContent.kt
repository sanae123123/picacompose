package com.picacompose.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.picacompose.picaapi.model.UserProfile
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon

/** 我的：资料明细 + 退出登录。 */
@Composable
fun MineContent(profile: UserProfile, onSignOut: () -> Unit) {
    val colors = MaterialTheme.colorScheme
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
        }
        item {
            PicaCard {
                DetailRow(PicaIcon.Person, "邮箱", profile.email ?: "未填")
                DetailRow(PicaIcon.Grid, "等级 / 经验", "Lv.${profile.level} · ${profile.exp}")
                DetailRow(PicaIcon.Home, "注册时间", profile.createdAt?.take(10) ?: "不知道")
                DetailRow(PicaIcon.Gear, "今日签到", if (profile.isPunched) "已签到" else "未签到")
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
