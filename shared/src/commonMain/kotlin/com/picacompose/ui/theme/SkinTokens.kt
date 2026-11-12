package com.picacompose.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.picacompose.ui.skin.SkinRegistry

/**
 * 当前皮肤的形状 / 表面参数。
 *
 * 这里只是转发，真正的数值在皮肤自己的 `tokens` 里（见 `ui/skin/BuiltinSkins.kt`）。
 * 页面里 `SkinTokens.cardRadius` 这种写法不用改，加皮肤时也不用动页面。
 */
object SkinTokens {
    /** 整块卡片：首页/设置/详情里那些。 */
    val cardRadius: Dp get() = SkinRegistry.current.tokens.cardRadius

    /** 列表行这种小一点的卡片。 */
    val rowRadius: Dp get() = SkinRegistry.current.tokens.rowRadius

    /** 自绘的小胶囊。 */
    val chipRadius: Dp get() = SkinRegistry.current.tokens.chipRadius

    /** 输入框。 */
    val fieldRadius: Dp get() = SkinRegistry.current.tokens.fieldRadius

    /** 卡片的分层方式：MD3 靠 tonalElevation。 */
    val cardTonalElevation: Dp get() = SkinRegistry.current.tokens.cardTonalElevation

    val cardShadow: Dp get() = SkinRegistry.current.tokens.cardShadow

    /** 卡片底色。 */
    @Composable
    fun cardColor(): Color = SkinRegistry.current.tokens.cardColor()
}
