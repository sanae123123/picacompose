package com.picacompose.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

private const val CHANNEL_ID = "downloads"
private const val NOTIFICATION_ID = 0x1D0

private var notifierContext: Context? = null
private val handler = Handler(Looper.getMainLooper())
private var pendingPost: Runnable? = null
private var lastNotice: DownloadNotice? = null
private var lastPostAt = 0L

/** 宿主在 onCreate 里调一次（和 `initDownloadStorage` 一起）。 */
fun initDownloadNotifier(context: Context) {
    notifierContext = context.applicationContext
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "本子缓存", NotificationManager.IMPORTANCE_LOW).apply {
            description = "缓存整本时的进度和速度"
            setShowBadge(false)
        },
    )
}

internal actual object DownloadNotifier {

    actual val supported: Boolean = true

    actual fun update(notice: DownloadNotice?) {
        val context = notifierContext ?: return
        pendingPost?.let { handler.removeCallbacks(it) }
        pendingPost = null
        if (notice == null) {
            lastNotice = null
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return
        }
        lastNotice = notice
        // 每下好一张图都会来一次，通知栏没必要跟着闪：400ms 内的更新合并成一次。
        val wait = 400L - (SystemClock.uptimeMillis() - lastPostAt)
        if (wait <= 0) {
            postProgress(context)
        } else {
            val runnable = Runnable { postProgress(context) }
            pendingPost = runnable
            handler.postDelayed(runnable, wait)
        }
    }
}

private fun postProgress(context: Context) {
    val notice = lastNotice ?: return
    lastPostAt = SystemClock.uptimeMillis()
    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val contentIntent = launch?.let {
        PendingIntent.getActivity(
            context,
            0,
            it,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    val books = notice.items.size
    val speed = formatSpeed(notice.bytesPerSecond)
    val title = when {
        books == 1 -> "正在缓存：${notice.items[0].title}"
        books > 1 -> "正在缓存 $books 本"
        else -> "正在缓存本子"
    }
    val text = buildString {
        if (notice.total > 0) {
            append("${notice.done}/${notice.total} 页")
        } else {
            append("正在拉图片列表")
        }
        if (notice.bytes > 0) {
            append(" · 已下 ${formatSize(notice.bytes)}")
        }
        if (notice.bytesPerSecond > 0) {
            append(" · $speed")
        }
    }
    val detail = buildString {
        for (item in notice.items.take(6)) {
            if (!item.title.isBlank()) {
                append(item.title)
                append("：")
            }
            append(if (item.total > 0) "${item.done}/${item.total} 页" else "准备中")
            append('\n')
        }
        if (notice.items.size > 6) append("…\n")
        append("总进度 ${notice.done}/${notice.total.coerceAtLeast(notice.done)} 页")
        if (notice.bytesPerSecond > 0) append(" · $speed")
    }

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
        .setProgress(notice.total.coerceAtLeast(1), notice.done, notice.total <= 0)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(contentIntent)
    runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${(kb * 10).toInt() / 10.0} KB"
    val mb = kb / 1024.0
    if (mb < 1024) return "${(mb * 10).toInt() / 10.0} MB"
    return "${(mb / 1024.0 * 10).toInt() / 10.0} GB"
}

private fun formatSpeed(bytesPerSecond: Long): String = "${formatSize(bytesPerSecond)}/s"
