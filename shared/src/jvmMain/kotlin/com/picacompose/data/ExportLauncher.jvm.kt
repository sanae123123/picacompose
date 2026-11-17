package com.picacompose.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Window
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// 有图形界面就能导出；纯无头环境（服务器）不露这个入口。
internal actual val exportSupported: Boolean = !java.awt.GraphicsEnvironment.isHeadless()

/** 和 Android 一样，导出跑在页面之外，退出详情页不打断。 */
private val exportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private var exportJob: Job? = null

@Composable
internal actual fun rememberExportLauncher(): ExportLauncher = remember {
    ExportLauncher(
        startBlock = { format, comicId, title -> pickAndStart(format, comicId, title) },
        backgroundBlock = { ExportProgressStore.update { it?.copy(background = true) } },
        cancelBlock = {
            exportJob?.cancel()
            exportJob = null
            ExportProgressStore.set(null)
            JvmExportNotifier.hide()
        },
    )
}

/** 弹 AWT 自带的保存对话框；选好位置再开始写。 */
private fun pickAndStart(format: ExportFormat, comicId: String, title: String) {
    if (exportJob?.isActive == true) return
    val owner = Window.getWindows().firstOrNull { it is Frame && it.isShowing } as? Frame
    val dialog = FileDialog(owner, "导出到哪里", FileDialog.SAVE).apply {
        file = exportFileName(title, format)
        isVisible = true
    }
    val directory = dialog.directory ?: return
    val name = dialog.file ?: return
    startExport(File(directory, name).absolutePath, format, comicId, title)
}

private fun startExport(targetPath: String, format: ExportFormat, comicId: String, title: String) {
    ExportProgressStore.set(
        ExportProgress(
            title = title,
            formatLabel = format.label,
            fraction = 0f,
            detail = "准备中",
        ),
    )
    exportJob = exportScope.launch {
        try {
            JvmExportEngine.export(targetPath, format, comicId, title) { fraction, detail ->
                ExportProgressStore.update { it?.copy(fraction = fraction, detail = detail) }
                publishToNotificationIfBackground()
            }
            ExportProgressStore.update { it?.copy(fraction = 1f, detail = "完成", finished = true) }
            ExportProgressStore.state.value?.takeIf { it.background }?.let { JvmExportNotifier.finish(it) }
        } catch (e: CancellationException) {
            ExportProgressStore.set(null)
            JvmExportNotifier.hide()
            throw e
        } catch (e: Exception) {
            ExportProgressStore.update { it?.copy(finished = true, error = e.message ?: "导出失败了") }
            ExportProgressStore.state.value?.takeIf { it.background }?.let { JvmExportNotifier.finish(it) }
        }
    }
}

private fun publishToNotificationIfBackground() {
    val progress = ExportProgressStore.state.value ?: return
    if (!progress.background) return
    JvmExportNotifier.start(progress.title)
    JvmExportNotifier.update(progress)
}

/** 文件名：书名里的斜杠、冒号之类会被系统对话框嫌弃，先抹掉。 */
private fun exportFileName(title: String, format: ExportFormat): String {
    val safe = title.replace(Regex("""[\\/:*?"<>|]"""), "").trim().ifBlank { "本子" }
    return "$safe.${format.ext}"
}
