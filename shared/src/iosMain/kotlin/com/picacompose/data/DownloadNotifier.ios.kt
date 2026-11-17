package com.picacompose.data

/** iOS 没有缓存入口，通知栏进度也就不存在。 */
internal actual object DownloadNotifier {
    actual val supported: Boolean = false
    actual fun update(notice: DownloadNotice?) = Unit
}
