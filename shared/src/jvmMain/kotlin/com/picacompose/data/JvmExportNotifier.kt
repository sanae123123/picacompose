package com.picacompose.data

import java.awt.Color
import java.awt.Graphics2D
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import javax.swing.Timer

/**
 * 桌面端的「通知栏」：系统托盘气泡。
 *
 * Windows 11 和 KDE 这类有托盘的桌面能弹通知；GNOME 默认没托盘，[supported] 是 false，
 * 只靠前台弹窗提示，别指望气泡。
 */
internal object JvmExportNotifier {

    private var tray: TrayIcon? = null
    private var lastUpdate = 0L

    /** 系统有没有托盘可用。 */
    val supported: Boolean
        get() = runCatching { SystemTray.isSupported() }.getOrDefault(false)

    /** 后台执行时开始把进度挂在托盘上。 */
    fun start(title: String) {
        if (!supported) return
        runCatching {
            if (tray == null) {
                tray = TrayIcon(trayIcon(), "PicaCompose").apply {
                    isImageAutoSize = true
                    SystemTray.getSystemTray().add(this)
                }
            }
            tray?.toolTip = "正在导出：$title"
        }
    }

    fun update(progress: ExportProgress) {
        val icon = tray ?: return
        val now = System.currentTimeMillis()
        // 托盘提示条别刷太勤，200ms 一次够了。
        if (now - lastUpdate < 200) return
        lastUpdate = now
        runCatching {
            icon.toolTip =
                "正在导出：${progress.title} ${(progress.fraction * 100).toInt()}% · ${progress.detail}"
        }
    }

    /** 完成/失败时弹一条系统通知，随后把托盘图标收掉。 */
    fun finish(progress: ExportProgress?) {
        val icon = tray ?: return
        tray = null
        val failed = progress?.error != null
        runCatching {
            icon.displayMessage(
                if (failed) "导出失败" else "导出完成",
                progress?.error ?: "文件已经写好了：${progress?.title.orEmpty()}",
                if (failed) TrayIcon.MessageType.ERROR else TrayIcon.MessageType.INFO,
            )
        }
        // 气泡刚弹出来就删图标会被吞掉，等它一下。
        Timer(1500) { SystemTray.getSystemTray().remove(icon) }.apply {
            isRepeats = false
            start()
        }
    }

    fun hide() {
        val icon = tray ?: return
        tray = null
        runCatching { SystemTray.getSystemTray().remove(icon) }
    }

    /** 托盘图标自己画一个，省得带资源文件。 */
    private fun trayIcon(): BufferedImage {
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val graphics: Graphics2D = image.createGraphics()
        try {
            graphics.color = Color(0x2F, 0x6F, 0xED)
            graphics.fillRoundRect(1, 1, 14, 14, 4, 4)
            graphics.color = Color.WHITE
            graphics.fillRect(7, 4, 2, 6)
            graphics.fillPolygon(intArrayOf(5, 11, 8), intArrayOf(9, 9, 13), 3)
        } finally {
            graphics.dispose()
        }
        return image
    }
}
