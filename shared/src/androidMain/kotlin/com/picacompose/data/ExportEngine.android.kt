package com.picacompose.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * 把下载好的缓存包导成文件。
 *
 * 真正写字节的逻辑在 jvmCommonMain 的 ExportWriters 里，Android 和桌面共用；
 * 这里只负责打开 SAF 的输出流、按格式分派。
 */
internal object ExportEngine {

    /**
     * [onProgress] 报进度：第一个参数 0..1，第二个是一句人话（`第 12/200 页` 之类）。
     * 在 IO 线程上回调，别在里面碰界面。
     */
    fun export(
        context: Context,
        uri: Uri,
        format: ExportFormat,
        comicId: String,
        title: String,
        onProgress: (Float, String) -> Unit,
    ) {
        val zipPath = ComicDownloader.cachedZipPath(comicId) ?: error("这本还没缓存好")
        if (!File(zipPath).exists()) error("缓存包不见了，可能刚被删掉")
        val output = context.contentResolver.openOutputStream(uri) ?: error("打不开保存位置")
        output.use { stream ->
            when (format) {
                ExportFormat.Zip -> copyZipExport(zipPath, stream, onProgress)
                ExportFormat.Pdf -> writePdfExport(zipPath, stream, onProgress)
                ExportFormat.Epub -> writeEpubExport(zipPath, title, stream, onProgress)
            }
        }
        // 流关掉之后才算真的写完：SAF 的 flush 都在这之后。
        onProgress(1f, "完成")
    }
}
