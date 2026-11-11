package com.picacompose.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.picacompose.data.platformKeyValueStore

/**
 * 皮肤。两套都跑在同一套 miuix 组件上，只换色板，所以切皮肤不会把界面结构换掉。
 *
 * - [Miuix]：miuix 自带的 HyperOS / MIUI 配色，白色背景 + MIUI 蓝。
 * - [MaterialYou]：Material You 的动态取色（Android 12 起跟着壁纸走），其余平台用
 *   MD3 的基线色板；卡片、底栏这些用的还是同一批组件，只是色值换成 MD3 的色值。
 */
enum class AppSkin(val label: String) {
    Miuix("MIUI 风格"),
    MaterialYou("Material You"),
}

/** 当前皮肤，改完直接落盘，下次冷启动照这个来。 */
object SkinState {
    private val kv by lazy { platformKeyValueStore() }

    var current: AppSkin by mutableStateOf(read())
        private set

    fun select(skin: AppSkin) {
        current = skin
        kv.put(KEY, skin.name)
    }

    private fun read(): AppSkin =
        runCatching { AppSkin.valueOf(kv.get(KEY).orEmpty()) }.getOrDefault(AppSkin.Miuix)

    private const val KEY = "ui.skin"
}
