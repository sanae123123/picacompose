package com.picacompose.data

import java.io.File

actual fun platformDownloadStore(): DownloadStore {
    val home = System.getProperty("user.home") ?: "."
    return JvmDownloadStore(File(home, ".picacompose/downloads"))
}
