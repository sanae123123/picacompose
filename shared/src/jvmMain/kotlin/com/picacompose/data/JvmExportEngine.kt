package com.picacompose.data

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * 桌面端的导出：选好路径后直接往下写。
 *
 * 真正写字节的逻辑和 Android 共用（jvmCommonMain 的 ExportWriters），
 * 这里只负责打开文件流、按格式分派。
 */
internal object JvmExportEngine {

    fun export(
        targetPath: String,
        format: ExportFormat,
        comicId: String,
        title: String,
        onProgress: (Float, String) -> Unit,
    ) {
        val zipPath = ComicDownloader.cachedZipPath(comicId) ?: error("这本还没缓存好")
        if (!File(zipPath).exists()) error("缓存包不见了，可能刚被删掉")
        val file = File(targetPath)
        file.parentFile?.mkdirs()
        BufferedOutputStream(FileOutputStream(file), 256 * 1024).use { stream ->
            when (format) {
                ExportFormat.Zip -> copyZipExport(zipPath, stream, onProgress)
                ExportFormat.Pdf -> writePdfExport(zipPath, stream, onProgress)
                ExportFormat.Epub -> writeEpubExport(zipPath, title, stream, onProgress)
            }
        }
        // 文件流关掉之后才算写完。
        onProgress(1f, "完成")
    }
}
