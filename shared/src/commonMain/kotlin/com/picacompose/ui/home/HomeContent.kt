package com.picacompose.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.picacompose.picaapi.model.UserProfile
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon

/** 主页：先放自己的资料和一些入口，推荐内容后面接。 */
@Composable
fun HomeContent(
    profile: UserProfile,
    onOpenCategory: () -> Unit,
    onOpenGame: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    PageScaffold {
        item {
            PageHeader(
                title = "欢迎回来",
                subtitle = "推荐位还在做，先看看别的。",
            )
            Spacer(Modifier.height(18.dp))
        }
        item {
            PicaCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = colors.primaryContainer,
                        modifier = Modifier.size(52.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = profile.name.take(1).ifBlank { "P" },
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.onPrimaryContainer,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = profile.name.ifBlank { "已登录" },
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoChip("Lv.${profile.level}")
                    InfoChip("经验 ${profile.exp}")
                    if (profile.isPunched) InfoChip("今日已签到")
                }
                val slogan = profile.slogan
                if (!slogan.isNullOrBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = slogan,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickCard(
                    icon = PicaIcon.Grid,
                    title = "分类",
                    subtitle = "按题材找漫画",
                    onClick = onOpenCategory,
                    modifier = Modifier.weight(1f),
                )
                QuickCard(
                    icon = PicaIcon.Gamepad,
                    title = "游戏",
                    subtitle = "站里的小游戏",
                    onClick = onOpenGame,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))
        }
        item {
            PicaCard {
                Text(
                    text = "首页推荐",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "轮播和榜单接口都已经通了，等推荐位设计好了接上。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QuickCard(
    icon: PicaIcon,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surface,
        tonalElevation = 1.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(colors.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PicaGlyph(icon, Modifier.size(18.dp), colors.onSecondaryContainer)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
        }
    }
}
