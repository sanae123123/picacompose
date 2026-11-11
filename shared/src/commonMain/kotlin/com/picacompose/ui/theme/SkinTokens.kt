package com.picacompose.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 两套皮肤共用的「形状 / 表面」参数。
 *
 * 组件本身还是同一套 miuix，这里只按皮肤调圆角和表面层：
 *
 * - MIUI 风格：大圆角（卡片 28dp、胶囊全圆）、卡片用淡蓝色。
 * - Material You：按 MD3 规格来——卡片 16dp、chip 8dp、输入框小圆角、表面用
 *   `surfaceContainerHigh` 这一层，卡片靠一层很淡的阴影和底色分开。
 */
object SkinTokens {
    private val materialYou: Boolean
        get() = SkinState.current == AppSkin.MaterialYou

    /** 整块卡片：首页/设置/详情里那些。 */
    val cardRadius: Dp get() = if (materialYou) 16.dp else 28.dp

    /** 列表行这种稍小一点的卡片。 */
    val rowRadius: Dp get() = if (materialYou) 16.dp else 22.dp

    /** 胶囊：MD3 的 chip 是 8dp 圆角，不是全圆。 */
    val chipRadius: Dp get() = if (materialYou) 8.dp else 999.dp

    /** 输入框。 */
    val fieldRadius: Dp get() = if (materialYou) 12.dp else 14.dp

    val cardShadow: Dp get() = if (materialYou) 1.dp else 0.dp

    /** 卡片底色：Material You 用带色调的表面层，MIUI 用淡蓝底。 */
    @Composable
    fun cardColor(): Color = MiuixTheme.colorScheme.let { colors ->
        if (materialYou) colors.surfaceContainerHigh else colors.tertiaryContainer
    }
}
