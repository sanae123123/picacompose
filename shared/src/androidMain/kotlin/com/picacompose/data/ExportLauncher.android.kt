package com.picacompose.data

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal actual val exportSupported: Boolean = true

private data class PendingExport(
    val format: ExportFormat,
    val comicId: String,
    val title: String,
)

@Composable
internal actual fun rememberExportLauncher(
    onResult: (ok: Boolean, message: String?) -> Unit,
): ExportLauncher {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestResult = rememberUpdatedState(onResult)
    var pending by remember { mutableStateOf<PendingExport?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val job = pending ?: return@rememberLauncherForActivityResult
        pending = null
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) {
            latestResult.value(false, null)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            // 打成 PDF/epub 可能要点时间，放 IO 线程，别卡着界面。
            val error = withContext(Dispatchers.IO) {
                runCatching {
                    ExportEngine.export(context, uri, job.format, job.comicId, job.title)
                }.exceptionOrNull()?.let { it.message ?: "导出失败了" }
            }
            latestResult.value(error == null, error)
        }
    }

    return remember(context) {
        ExportLauncher { format, comicId, title ->
            pending = PendingExport(format, comicId, title)
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = format.mime
                putExtra(Intent.EXTRA_TITLE, exportFileName(title, format))
            }
            runCatching { launcher.launch(intent) }.onFailure {
                pending = null
                latestResult.value(false, "手机上没找到能保存文件的应用")
            }
        }
    }
}

/** 文件名：书名里的斜杠、冒号之类会被系统对话框嫌弃，先抹掉。 */
private fun exportFileName(title: String, format: ExportFormat): String {
    val safe = title.replace(Regex("""[\\/:*?"<>|]"""), "").trim().ifBlank { "本子" }
    return "$safe.${format.ext}"
}
