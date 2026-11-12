package com.picacompose.ui.theme

import com.picacompose.ui.skin.PicaSkin
import com.picacompose.ui.skin.SkinRegistry

/**
 * 皮肤状态的门面。
 *
 * 真正的注册表在 [SkinRegistry]（`ui/skin` 包）里，页面和设置页只跟这里打交道。
 * 加一套皮肤：在 `ui/skin` 里新增一个 [PicaSkin] 实现、注册进 [SkinRegistry] 就行，
 * 设置页的列表会自己多出来一行，不用改 UI 代码。
 */
object SkinState {
    /** 当前皮肤。读它会订阅切换，皮肤一变整棵树重组。 */
    val current: PicaSkin get() = SkinRegistry.current

    /** 所有注册过的皮肤，顺序就是设置页里的顺序。 */
    val skins: List<PicaSkin> get() = SkinRegistry.skins

    /** 切到某套皮肤，顺便落盘。 */
    fun select(skin: PicaSkin) = SkinRegistry.select(skin)
}
