package com.picacompose.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal actual val exportSupported: Boolean = true

/**
 * 导出的协程跑在这个 scope 上，不是页面的 scope。
 *
 * 「后台执行」之后用户可能退出详情页，任务不能跟着页面一起取消。
 */
private val exportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private var exportJob: Job? = null

private data class PendingExport(
    val format: ExportFormat,
    val comicId: String,
    val title: String,
)

@Composable
internal actual fun rememberExportLauncher(): ExportLauncher {
    val context = LocalContext.current
    val latestContext = rememberUpdatedState(context)
    var pending by remember { mutableStateOf<PendingExport?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val job = pending ?: return@rememberLauncherForActivityResult
        pending = null
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) return@rememberLauncherForActivityResult
        startExport(latestContext.value, uri, job)
    }

    return remember {
        ExportLauncher(
            startBlock = { format, comicId, title ->
                // 一次只导一本，正在导的时候再点别的格式就等着当前这次。
                if (exportJob?.isActive != true) {
                    pending = PendingExport(format, comicId, title)
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = format.mime
                        putExtra(Intent.EXTRA_TITLE, exportFileName(title, format))
                    }
                    runCatching { picker.launch(intent) }.onFailure {
                        pending = null
                        ExportProgressStore.set(
                            ExportProgress(
                                title = title,
                                formatLabel = format.label,
                                fraction = 0f,
                                detail = "打不开保存位置",
                                finished = true,
                                error = "手机上没找到能保存文件的应用",
                            ),
                        )
                    }
                }
            },
            backgroundBlock = {
                ExportProgressStore.update { it?.copy(background = true) }
            },
            cancelBlock = { cancelExport() },
        )
    }
}

private fun startExport(context: Context, uri: Uri, pending: PendingExport) {
    ExportProgressStore.set(
        ExportProgress(
            title = pending.title,
            formatLabel = pending.format.label,
            fraction = 0f,
            detail = "准备中",
        ),
    )
    exportJob = exportScope.launch {
        try {
            ExportEngine.export(context, uri, pending.format, pending.comicId, pending.title) { fraction, detail ->
                ExportProgressStore.update { it?.copy(fraction = fraction, detail = detail) }
                publishToNotificationIfBackground()
            }
            ExportProgressStore.update { it?.copy(fraction = 1f, detail = "完成", finished = true) }
            ExportNotifier.update(null)
        } catch (e: CancellationException) {
            ExportProgressStore.set(null)
            ExportNotifier.update(null)
            throw e
        } catch (e: Exception) {
            ExportProgressStore.update {
                it?.copy(finished = true, error = e.message ?: "导出失败了")
            }
            ExportNotifier.update(null)
        }
    }
}

private fun publishToNotificationIfBackground() {
    val progress = ExportProgressStore.state.value ?: return
    if (progress.background) ExportNotifier.update(progress)
}

private fun cancelExport() {
    exportJob?.cancel()
    exportJob = null
    ExportProgressStore.set(null)
    ExportNotifier.update(null)
}

/** 文件名：书名里的斜杠、冒号之类会被系统对话框嫌弃，先抹掉。 */
private fun exportFileName(title: String, format: ExportFormat): String {
    val safe = title.replace(Regex("""[\\/:*?"<>|]"""), "").trim().ifBlank { "本子" }
    return "$safe.${format.ext}"
}
