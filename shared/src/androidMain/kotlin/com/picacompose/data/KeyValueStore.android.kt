package com.picacompose.data

import android.content.Context
import android.content.SharedPreferences

private var appContext: Context? = null

/** Android 宿主在 onCreate 里调一次，把 applicationContext 交进来。 */
fun initPicaStorage(context: Context) {
    appContext = context.applicationContext
}

actual fun platformKeyValueStore(): KeyValueStore {
    val ctx: Context = appContext ?: error("initPicaStorage(context) 还没调用")
    val prefs: SharedPreferences = ctx.getSharedPreferences("pica_session", Context.MODE_PRIVATE)
    return object : KeyValueStore {
        override fun get(key: String): String? = prefs.getString(key, null)

        override fun put(key: String, value: String?) {
            val editor = prefs.edit()
            if (value == null) editor.remove(key) else editor.putString(key, value)
            editor.apply()
        }
    }
}
