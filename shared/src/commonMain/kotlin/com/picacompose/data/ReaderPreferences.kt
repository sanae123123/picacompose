package com.picacompose.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** 阅读器的偏好。 */
class ReaderPreferences(private val kv: KeyValueStore = platformKeyValueStore()) {

    /**
     * 音量键翻页：开着时阅读器里音量键上下翻一页，不调音量。默认开。
     *
     * 改完立即生效：按键时现读一次，不用重进阅读器。
     */
    var volumeKeysTurnPage: Boolean
        get() = kv.get(KEY_VOLUME_KEYS)?.toBooleanStrictOrNull() ?: true
        set(value) {
            kv.put(KEY_VOLUME_KEYS, value.toString())
        }

    private companion object {
        const val KEY_VOLUME_KEYS = "reader.volumeKeysTurnPage"
    }
}

/**
 * 音量键翻页的桥：系统音量键先到 Android 的 Activity，Activity 拦下来扔进这里，
 * 由阅读器接走翻页。
 *
 * 阅读器不在前台、或者设置里这个开关关着，就原样交回系统去调音量，
 * 不占着音量键不放。
 */
object ReaderVolumeKeys {

    /** 阅读器是否在前台；进阅读器时置真，退出时置假。 */
    var active: Boolean = false

    private val _events = MutableSharedFlow<Int>(extraBufferCapacity = 2)

    /** 一次翻页：-1 上一页，+1 下一页。 */
    val events: SharedFlow<Int> = _events

    private val preferences = ReaderPreferences()

    /**
     * 音量键进来。返回 true 表示这一次按键被翻页吃掉，不要再交给系统。
     *
     * [action] 0=按下 1=抬起，[keyCode] 24=音量加 25=音量减。
     * 抬起也一起吞掉，长按才不会一路把音量调上去。
     */
    fun onVolumeKey(action: Int, keyCode: Int, repeatCount: Int): Boolean {
        if (!active) return false
        if (!preferences.volumeKeysTurnPage) return false
        val direction = when (keyCode) {
            KEY_VOLUME_UP -> -1
            KEY_VOLUME_DOWN -> 1
            else -> return false
        }
        // 只认第一次按下：长按的重复事件吞掉但不连翻，翻页节奏才跟手。
        if (action == ACTION_DOWN && repeatCount == 0) _events.tryEmit(direction)
        return true
    }

    private const val ACTION_DOWN = 0
    private const val KEY_VOLUME_UP = 24
    private const val KEY_VOLUME_DOWN = 25
}
