package com.picacompose.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.picacompose.data.pica
import com.picacompose.picaapi.model.UserProfile
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.UserIntroDialog
import com.picacompose.ui.components.UserTitleBadge
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.url
import kotlinx.coroutines.launch

/** 我的：头像 + 简介 + 资料明细 + 签到 + 退出登录。 */
@Composable
fun MineContent(profile: UserProfile, onSignOut: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    // 签到成功后本地的 isPunched 会立刻翻，不等下次拉 profile。
    var checkedIn by remember(profile) { mutableStateOf(profile.isPunched) }
    var punching by remember { mutableStateOf(false) }
    var punchError by remember { mutableStateOf<String?>(null) }

    val slogan = profile.slogan?.trim().orEmpty()
    val avatarUrl = profile.avatar.url()
    // 简介两行放不下才给「展开」，点开是能滚的弹窗。
    var sloganOverflows by remember(slogan) { mutableStateOf(false) }
    var showSlogan by remember(slogan) { mutableStateOf(false) }

    PageScaffold(
        header = { PageHeader(title = "我的", subtitle = "当前登录的账号。") },
    ) {
        item {
            PicaCard {
                Box(Modifier.fillMaxWidth()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // 底下垫首字母，头像没加载出来（或没有头像）时不至于空一块。
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
                                if (avatarUrl != null) {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = profile.name.ifBlank { "已登录" },
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onSurface,
                            )
                            UserTitleBadge(profile.title)
                        }
                        if (slogan.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = slogan,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                                onTextLayout = { sloganOverflows = it.hasVisualOverflow },
                            )
                            if (sloganOverflows) {
                                Text(
                                    text = "展开",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.primary,
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .padding(top = 4.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { showSlogan = true }
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    // 头像卡的右上角，只留一个图标；签过了就不出现。
                    if (!checkedIn) {
                        FilledTonalIconButton(
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
                            modifier = Modifier.align(Alignment.TopEnd).size(44.dp),
                        ) {
                            if (punching) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                PicaGlyph(PicaIcon.CheckIn, Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
            val error = punchError
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
        }
        item {
            PicaCard {
                DetailRow(PicaIcon.Person, "邮箱", profile.email ?: "未填")
                DetailRow(PicaIcon.Grid, "等级 / 经验", "Lv.${profile.level} · ${profile.exp}")
                LevelProgress(level = profile.level, exp = profile.exp)
                DetailRow(PicaIcon.Home, "注册时间", profile.createdAt?.take(10) ?: "不知道")
                DetailRow(PicaIcon.Gear, "今日签到", if (checkedIn) "已签到" else "未签到")
            }
            Spacer(Modifier.height(14.dp))
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

    if (showSlogan) {
        UserIntroDialog(
            name = profile.name,
            avatarUrl = avatarUrl,
            title = profile.title,
            slogan = slogan,
            onDismiss = { showSlogan = false },
        )
    }
}

/**
 * 等级 L 的起算经验。
 *
 * 官方没给公式，这是拿骑士榜上 50 个用户的 level / exp 对出来的：`100 * L * (L-1)`，
 * 即 Lv2=200、Lv3=600、Lv4=1200……所有样本都落在 `[threshold(L), threshold(L+1))` 里。
 */
private fun levelStartExp(level: Int): Int = 100 * level * (level - 1)

/** 到下一等级的进度条。 */
@Composable
private fun LevelProgress(level: Int, exp: Int) {
    val colors = MaterialTheme.colorScheme
    val start = levelStartExp(level)
    val next = levelStartExp(level + 1)
    val span = (next - start).coerceAtLeast(1)
    val fraction = ((exp - start).toFloat() / span).coerceIn(0f, 1f)
    val remaining = (next - exp).coerceAtLeast(0)

    Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp)) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = colors.primary,
            trackColor = colors.surfaceVariant,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$exp / $next",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "还差 $remaining 到 Lv.${level + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
