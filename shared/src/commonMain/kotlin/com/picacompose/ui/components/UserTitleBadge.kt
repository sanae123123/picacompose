package com.picacompose.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 昵称旁边的称号小徽章（「萌新」「绅士」这类）。
 *
 * 没有称号就什么都不画，调用方不用自己判空。
 */
@Composable
fun UserTitleBadge(title: String?, modifier: Modifier = Modifier) {
    val text = title?.trim().orEmpty()
    if (text.isEmpty()) return
    val colors = MiuixTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

/**
 * 接口里的性别代码转官方文案：`m` = 绅士，`f` = 淑女，`bot` = 机械人。
 *
 * 认不出来（或者压根没填）就返回 null，调用方不显示就是了。
 */
fun genderLabel(raw: String?): String? = when (raw?.trim()?.lowercase()) {
    "m", "male" -> "绅士"
    "f", "female" -> "淑女"
    "bot", "robot" -> "机械人"
    else -> null
}

/** 简介里那行资料：`绅士 · Lv.3 · 经验 1234`，缺哪样就不写哪样。 */
fun userDetailLine(
    gender: String? = null,
    level: Int? = null,
    exp: Int? = null,
): String? = listOfNotNull(
    genderLabel(gender),
    level?.let { "Lv.$it" },
    exp?.let { "经验 $it" },
).joinToString(" · ").takeIf { it.isNotBlank() }

/**
 * 等级 [level] 的起算经验。
 *
 * 官方没给公式，这是拿骑士榜上 50 个用户的 level / exp 对出来的：`100 * L * (L-1)`，
 * 即 Lv2=200、Lv3=600、Lv4=1200……所有样本都落在 `[threshold(L), threshold(L+1))` 里。
 */
fun levelStartExp(level: Int): Int = 100 * level * (level - 1)
