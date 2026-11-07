package com.picacompose.data

/** 极简键值存储，只用来记住登录状态，三端各给一份实现。 */
interface KeyValueStore {
    fun get(key: String): String?

    /** 传 null 就是删掉这个键。 */
    fun put(key: String, value: String?)
}

/**
 * 平台默认实现：Android = SharedPreferences，Desktop = java.util.prefs，iOS = NSUserDefaults。
 * Android 需要宿主在 onCreate 里先调 `initPicaStorage(context)`。
 */
expect fun platformKeyValueStore(): KeyValueStore
