package com.picacompose.ui.skin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.picacompose.data.platformKeyValueStore

/**
 * 皮肤注册表。
 *
 * 内置两套（MIUI 风格、Material You），启动时注册进来。想加皮肤的话：
 *
 * 1. 代码里新增一个 [PicaSkin] 实现，调 [register] 挂上来；
 * 2. 以后做插件安装的话，在启动时从皮肤包里读出数据、构造成 [PicaSkin] 再调 [register]，
 *    上层页面完全不用知道皮肤是从哪来的。
 *
 * 选中的皮肤按 [PicaSkin.id] 存进本地 KV，冷启动照这个恢复；存的值找不到对应皮肤时退回
 * 第一套，不会因为卸载了某个皮肤就直接崩掉。
 */
object SkinRegistry {
    private val _skins = mutableStateListOf<PicaSkin>()

    /** 注册过的皮肤，顺序就是设置页里的顺序。 */
    val skins: List<PicaSkin> get() = _skins

    private var currentId by mutableStateOf(readId())

    /** 当前皮肤。 [skins] 为空时兜底给 MIUI 风格。 */
    val current: PicaSkin
        get() = _skins.firstOrNull { it.id == currentId } ?: _skins.firstOrNull() ?: MiuixSkin

    init {
        register(MiuixSkin)
        register(MaterialYouSkin)
    }

    /** 挂一套皮肤上来；同 id 的会替换掉旧的。 */
    fun register(skin: PicaSkin) {
        val existing = _skins.indexOfFirst { it.id == skin.id }
        if (existing >= 0) _skins[existing] = skin else _skins.add(skin)
        // 之前存的是这套皮肤、但包刚装回来，这里顺手把选择对上。
        if (skin.id == currentId) currentId = skin.id
    }

    /** 选中一套已注册的皮肤。 */
    fun select(skin: PicaSkin) {
        register(skin)
        select(skin.id)
    }

    /** 按 id 选中；没注册过就什么都不做。 */
    fun select(id: String) {
        if (_skins.none { it.id == id }) return
        currentId = id
        writeId(id)
    }

    private val kv by lazy { platformKeyValueStore() }

    private fun readId(): String {
        val raw = runCatching { kv.get(KEY).orEmpty() }.getOrDefault("")
        // 兼容旧版本存的是枚举名。
        val migrated = when (raw) {
            "Miuix" -> MiuixSkin.id
            "MaterialYou" -> MaterialYouSkin.id
            else -> raw
        }
        return migrated.ifEmpty { MiuixSkin.id }
    }

    private fun writeId(id: String) {
        runCatching { kv.put(KEY, id) }
    }

    private const val KEY = "ui.skin"
}
