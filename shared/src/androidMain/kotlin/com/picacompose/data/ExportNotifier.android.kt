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

private const val CHANNEL_ID = "exports"
private const val NOTIFICATION_ID = 0x1E0

private var notifierContext: Context? = null
private val handler = Handler(Looper.getMainLooper())
private var pendingPost: Runnable? = null
private var lastProgress: ExportProgress? = null
private var lastPostAt = 0L

/** 宿主在 onCreate 里调一次（和 `initDownloadNotifier` 一起）。 */
fun initExportNotifier(context: Context) {
    notifierContext = context.applicationContext
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "导出本子", NotificationManager.IMPORTANCE_LOW).apply {
            description = "导出文件时的进度"
            setShowBadge(false)
        },
    )
}

/** 「后台执行」之后，导出进度挂到通知栏；null 表示收工撤掉。 */
internal object ExportNotifier {

    fun update(progress: ExportProgress?) {
        val context = notifierContext ?: return
        pendingPost?.let { handler.removeCallbacks(it) }
        pendingPost = null
        if (progress == null) {
            lastProgress = null
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return
        }
        lastProgress = progress
        // 写文件时进度来得很快，通知栏 300ms 合并一次就够了。
        val wait = 300L - (SystemClock.uptimeMillis() - lastPostAt)
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
    val progress = lastProgress ?: return
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
    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setContentTitle("正在导出：${progress.title}")
        .setContentText("${progress.formatLabel} · ${progress.detail}")
        .setProgress(
            100,
            (progress.fraction * 100).toInt().coerceIn(0, 100),
            progress.fraction <= 0f,
        )
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(contentIntent)
    runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
}
