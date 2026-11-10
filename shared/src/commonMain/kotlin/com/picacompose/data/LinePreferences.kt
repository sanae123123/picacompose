package com.picacompose.data

/**
 * 线路开关，存在本机。
 *
 * - [autoProbe]：启动时测一遍线路、挑最快的用。关掉就直接按上次的线路进，不测速，
 *   也不会因为有更快的就自己换线（连不上时仍会用现有 IP 池把这条线路重建一次）。
 * - [fastEntry]：[autoProbe] 打开时才有意义。开着是先按上次的线路进主界面、测速丢后台；
 *   关掉就停在线路选择页，等测完再进。
 *
 * 两个都默认开，和之前的行为一致。
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

    private companion object {
        const val KEY_AUTO_PROBE = "route.autoProbe"
        const val KEY_FAST_ENTRY = "route.fastEntry"
    }
}
