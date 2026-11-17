package com.picacompose.data

import android.content.Context
import java.io.File

private var downloadContext: Context? = null

/** Android 宿主在 onCreate 里调一次，把 applicationContext 交进来（和 `initPicaStorage` 一起）。 */
fun initDownloadStorage(context: Context) {
    downloadContext = context.applicationContext
}

actual fun platformDownloadStore(): DownloadStore {
    val ctx: Context = downloadContext ?: error("initDownloadStorage(context) 还没调用")
    return JvmDownloadStore(File(ctx.filesDir, "downloads"))
}
