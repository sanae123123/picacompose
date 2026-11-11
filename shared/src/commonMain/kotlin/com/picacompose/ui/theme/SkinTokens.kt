package com.picacompose.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 两套皮肤共用的「形状 / 表面」参数，口径照各自的设计语言来。
 *
 * - MIUI 风格：大圆角卡片、全圆胶囊、淡蓝卡片底。
 * - Material You：MD3 的 28dp 大卡片、16dp 列表卡、全圆信息胶囊、
 *   表面层用 `surface` + `tonalElevation`（就是切到 miuix 之前那套观感）。
 */
object SkinTokens {
    internal val materialYou: Boolean
        get() = SkinState.current == AppSkin.MaterialYou

    /** 整块卡片：首页/设置/详情里那些。 */
    val cardRadius: Dp get() = 28.dp

    /** 列表行这种小一点的卡片。 */
    val rowRadius: Dp get() = if (materialYou) 16.dp else 22.dp

    /** 自绘的小胶囊：两套都是全圆。 */
    val chipRadius: Dp get() = 999.dp

    /** 输入框：Material You 走 MD3 的 4dp 圆角。 */
    val fieldRadius: Dp get() = if (materialYou) 4.dp else 14.dp

    /** MD3 的卡片靠 tonalElevation 分层，不靠阴影。 */
    val cardTonalElevation: Dp get() = if (materialYou) 1.dp else 0.dp

    val cardShadow: Dp get() = 0.dp

    /** 卡片底色：Material You 用 surface，MIUI 用淡蓝。 */
    @Composable
    fun cardColor(): Color = MiuixTheme.colorScheme.let { colors ->
        if (materialYou) colors.surface else colors.tertiaryContainer
    }
}
