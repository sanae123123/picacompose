package com.picacompose.data

import java.util.prefs.Preferences

actual fun platformKeyValueStore(): KeyValueStore {
    val prefs: Preferences = Preferences.userRoot().node("com/picacompose")
    return object : KeyValueStore {
        override fun get(key: String): String? = prefs.get(key, null)

        override fun put(key: String, value: String?) {
            if (value == null) prefs.remove(key) else prefs.put(key, value)
        }
    }
}
