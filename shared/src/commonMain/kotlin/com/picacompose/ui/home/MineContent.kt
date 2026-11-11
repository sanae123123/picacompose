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
import com.picacompose.ui.components.PicaAlertDialog
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import com.picacompose.ui.theme.SkinTokens
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.picacompose.ui.components.PicaOutlinedButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import com.picacompose.ui.components.PicaTextButton
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
import com.picacompose.data.readingProgressStore
import com.picacompose.picaapi.model.UserProfile
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.UserIntroDialog
import com.picacompose.ui.components.UserAvatar
import com.picacompose.ui.components.rememberDialogPop
import com.picacompose.ui.components.UserTitleBadge
import com.picacompose.ui.components.genderLabel
import com.picacompose.ui.components.levelStartExp
import com.picacompose.ui.components.userDetailLine
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.url
import kotlinx.coroutines.launch

/** 签到结果的一张小卡片文案。 */
private data class PunchNotice(val title: String, val message: String)

/** 我的：头像 + 简介 + 资料明细 + 签到 + 退出登录。 */
@Composable
fun MineContent(
    profile: UserProfile,
    onSignOut: () -> Unit,
    onRefreshProfile: suspend () -> Unit = {},
    onOpenFavourites: () -> Unit = {},
    onOpenComments: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    /** 右上角「编辑资料」，点开从这颗按钮长成整页。 */
    onOpenEditProfile: () -> Unit = {},
    /** 三张卡各挂一个变形源，点开时从小卡片长成整页（key 和整页那边配对）。 */
    favouritesModifier: Modifier = Modifier,
    commentsModifier: Modifier = Modifier,
    historyModifier: Modifier = Modifier,
    editProfileModifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    // 签到成功后本地的 isPunched 会立刻翻，不等下次拉 profile。
    var checkedIn by remember(profile) { mutableStateOf(profile.isPunched) }
    var punching by remember { mutableStateOf(false) }
    var punchError by remember { mutableStateOf<String?>(null) }
    // 签到结果弹窗：成功了要有反馈，不然点完图标什么都没发生。
    var punchNotice by remember { mutableStateOf<PunchNotice?>(null) }

    val slogan = profile.slogan?.trim().orEmpty()
    val avatarUrl = profile.avatar.url()
    val avatarFrame = profile.character
    // 简介两行放不下才给「展开」，点开是能滚的弹窗。
    var sloganOverflows by remember(slogan) { mutableStateOf(false) }
    var showSlogan by remember(slogan) { mutableStateOf(false) }

    PageScaffold(
        header = {
            PageHeader(
                title = "我的",
                action = {
                    IconButton(
                        onClick = onOpenEditProfile,
                        modifier = editProfileModifier.size(40.dp),
                    ) {
                        PicaGlyph(icon = PicaIcon.Edit, modifier = Modifier.size(20.dp))
                    }
                },
            )
        },
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
                            shape = RoundedCornerShape(SkinTokens.chipRadius),
                            color = colors.primaryContainer,
                            modifier = Modifier.size(72.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = profile.name.take(1).ifBlank { "P" },
                                    style = MiuixTheme.textStyles.title2,
                                    color = colors.onPrimaryContainer,
                                )
                                if (avatarUrl != null) {
                                    UserAvatar(
                                        avatarUrl = avatarUrl,
                                        frameUrl = avatarFrame,
                                        size = 72.dp,
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
                                style = MiuixTheme.textStyles.title4,
                                color = colors.onSurface,
                            )
                            UserTitleBadge(profile.title)
                        }
                        if (slogan.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = slogan,
                                style = MiuixTheme.textStyles.body1,
                                color = colors.onSurfaceSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                                onTextLayout = { sloganOverflows = it.hasVisualOverflow },
                            )
                            if (sloganOverflows) {
                                Text(
                                    text = "展开",
                                    style = MiuixTheme.textStyles.footnote1,
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
                        IconButton(
                            onClick = {
                                scope.launch {
                                    punching = true
                                    punchError = null
                                    try {
                                        pica.users.punchIn()
                                        checkedIn = true
                                        punchNotice = PunchNotice(
                                            title = "签到成功",
                                            message = "今天的签到已经记下了，明天再来。",
                                        )
                                        // 经验 / 等级 / 签到状态一起刷新，别让这一页停在旧数据上。
                                        onRefreshProfile()
                                    } catch (e: Exception) {
                                        // 可能是「今天已经签过了」而本地状态过期，问一次服务器再决定。
                                        val fresh = catchingLoad { pica.users.profile() }.getOrNull()
                                        if (fresh?.isPunched == true) {
                                            checkedIn = true
                                            punchNotice = PunchNotice(
                                                title = "今天已经签过了",
                                                message = "服务器那边显示今天已经签到，不用再点啦。",
                                            )
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
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.error,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
        }
        item {
            PicaCard {
                DetailRow(PicaIcon.Person, "邮箱", profile.email ?: "未填")
                DetailRow(PicaIcon.Person, "性别", genderLabel(profile.gender) ?: "未填")
                DetailRow(PicaIcon.Grid, "等级 / 经验", "Lv.${profile.level} · ${profile.exp}")
                LevelProgress(level = profile.level, exp = profile.exp)
                DetailRow(PicaIcon.Home, "注册时间", profile.createdAt?.take(10) ?: "不知道")
                DetailRow(PicaIcon.Gear, "今日签到", if (checkedIn) "已签到" else "未签到")
            }
            Spacer(Modifier.height(14.dp))
        }
        item {
            // 三张入口卡：收藏、历史评论、浏览历史，各自长成整页。
            PicaCard {
                MineEntryRow(
                    icon = PicaIcon.Star,
                    title = "我的收藏",
                    summary = "收藏过的本子都在这儿",
                    modifier = favouritesModifier,
                    onClick = onOpenFavourites,
                )
                MineEntryRow(
                    icon = PicaIcon.Notice,
                    title = "历史评论",
                    summary = "我发过的评论，点一条回到那本本子",
                    modifier = commentsModifier,
                    onClick = onOpenComments,
                )
                MineEntryRow(
                    icon = PicaIcon.Eye,
                    title = "浏览历史",
                    summary = historySummary(),
                    modifier = historyModifier,
                    onClick = onOpenHistory,
                )
            }
            Spacer(Modifier.height(14.dp))
        }
        item {
            PicaOutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.height(52.dp).fillMaxWidth(),
            ) {
                PicaGlyph(PicaIcon.Logout, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("退出登录", style = MiuixTheme.textStyles.button)
            }
        }
    }

    punchNotice?.let { notice ->
        val pop = rememberDialogPop(onDismiss = { punchNotice = null })
        PicaAlertDialog(
            onDismissRequest = pop::dismiss,
            modifier = pop.modifier,
            title = {
                Text(text = notice.title, style = MiuixTheme.textStyles.title4)
            },
            text = {
                Text(
                    text = notice.message,
                    style = MiuixTheme.textStyles.body1,
                    color = colors.onSurfaceSecondary,
                )
            },
            confirmButton = {
                PicaTextButton(onClick = pop::dismiss) { Text("好") }
            },
        )
    }

    if (showSlogan) {
        UserIntroDialog(
            name = profile.name,
            avatarUrl = avatarUrl,
            frameUrl = avatarFrame,
            title = profile.title,
            slogan = slogan,
            detail = userDetailLine(
                gender = profile.gender,
                level = profile.level,
                exp = profile.exp,
            ),
            onDismiss = { showSlogan = false },
        )
    }
}

/** 「浏览历史」那行的副标题：本机记了多少本。 */
private fun historySummary(): String {
    val count = readingProgressStore.all().size
    return if (count > 0) "最近翻过 $count 本" else "还没翻过本子"
}

/**
 * 我的页里的一张入口卡：图标 + 标题 + 说明 + 右箭头，整行可点。
 *
 * [modifier] 用来挂共享元素变形源，所以不能省。
 */
@Composable
private fun MineEntryRow(
    icon: PicaIcon,
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(SkinTokens.rowRadius),
        color = colors.surface,
        modifier = modifier.fillMaxWidth().padding(vertical = 5.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PicaGlyph(icon, Modifier.size(20.dp), colors.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.subtitle,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            PicaGlyph(PicaIcon.ArrowRight, Modifier.size(18.dp), colors.onSurfaceSecondary)
        }
    }
}

/** 到下一等级的进度条。 */
@Composable
private fun LevelProgress(level: Int, exp: Int) {
    val colors = MiuixTheme.colorScheme
    val start = levelStartExp(level)
    val next = levelStartExp(level + 1)
    val span = (next - start).coerceAtLeast(1)
    val fraction = ((exp - start).toFloat() / span).coerceIn(0f, 1f)
    val remaining = (next - exp).coerceAtLeast(0)

    Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp)) {
        LinearProgressIndicator(
colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = colors.primary, backgroundColor = colors.surfaceVariant),
progress = fraction,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$exp / $next",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "还差 $remaining 到 Lv.${level + 1}",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
        }
    }
}
