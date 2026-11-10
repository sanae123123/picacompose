package com.picacompose.data

import com.picacompose.picaapi.DohAnswer
import com.picacompose.picaapi.dohAnswer

/**
 * 线路开关，存在本机。
 *
 * - [autoProbe]：启动时测一遍线路、挑最快的用。关掉就直接按上次的线路进，不测速，
 *   也不会因为有更快的就自己换线（连不上时仍会用现有 IP 池把这条线路重建一次）。
 * - [fastEntry]：[autoProbe] 打开时才有意义。开着是先按上次的线路进主界面、测速丢后台；
 *   关掉就停在线路选择页，等测完再进。
 * - [doh]：启动时额外用 DoH 解析一次接口域名，拿到的地址并进 IP 池（排在 /init 下发的
 *   前面）。默认关。
 *
 * 前两个默认开，和之前的行为一致。
 */
class LinePreferences(private val kv: KeyValueStore = platformKeyValueStore()) {

    var autoProbe: Boolean
        get() = kv.get(KEY_AUTO_PROBE) != "0"
        set(value) {
            kv.put(KEY_AUTO_PROBE, if (value) "1" else "0")
        }

    var fastEntry: Boolean
        get() = kv.get(KEY_FAST_ENTRY) != "0"
        set(value) {
            kv.put(KEY_FAST_ENTRY, if (value) "1" else "0")
        }

    var doh: Boolean
        get() = kv.get(KEY_DOH) == "1"
        set(value) {
            kv.put(KEY_DOH, if (value) "1" else "0")
        }

    companion object {
        /**
         * DoH 一个可信地址都解析不出来、被自动关掉时想跟用户说一句（App 接到 toast 上）。
         *
         * 开关在设置页里能手动开回来；这里只负责「别让它一直躺在一个坏掉的状态上」。
         */
        var onDohDisabled: ((String) -> Unit)? = null

        private const val KEY_AUTO_PROBE = "route.autoProbe"
        private const val KEY_FAST_ENTRY = "route.fastEntry"
        private const val KEY_DOH = "route.doh"
    }
}

/**
 * 开关打开时才去问一次 DoH；关着或者没问出来都是空列表，调用方不用自己判。
 *
 * 一个可信地址都没拿到就把开关自动关掉：留着它只会让每次冷启动白等一遍 DoH，
 * 然后还是退回系统 DNS。只有国内兜底端点答上来时，那份地址可能是被污染过的，
 * 能和已知 IP 池（[knownIps]）对上才敢用。
 */
internal suspend fun dohAddressesIfEnabled(
    preferences: LinePreferences,
    knownIps: List<String> = emptyList(),
): List<String> {
    if (!preferences.doh) return emptyList()
    val answer = runCatching { dohAnswer() }.getOrNull() ?: DohAnswer()
    val usable = when {
        answer.addresses.isEmpty() -> emptyList()
        answer.trusted -> answer.addresses
        answer.addresses.any { it in knownIps } -> answer.addresses
        else -> emptyList()
    }
    if (usable.isEmpty()) {
        preferences.doh = false
        LinePreferences.onDohDisabled?.invoke("DoH 解析不通，已自动关掉这个开关，这次按系统 DNS 走")
    }
    return usable
}
