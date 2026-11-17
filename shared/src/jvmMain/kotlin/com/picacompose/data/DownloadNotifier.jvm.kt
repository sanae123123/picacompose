package com.picacompose.data

// 桌面端没有通知栏进度，如实说没有。
internal actual object DownloadNotifier {
    actual val supported: Boolean = false

    actual fun update(notice: DownloadNotice?) = Unit
}
