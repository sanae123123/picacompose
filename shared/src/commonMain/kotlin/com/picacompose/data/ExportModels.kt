package com.picacompose.data

import androidx.compose.runtime.Composable

/** 把缓存好的本子导成文件的几种格式。 */
internal enum class ExportFormat(
    val label: String,
    val hint: String,
    val ext: String,
    val mime: String,
) {
    Zip("压缩包", "原样打包，换台设备还能导回来", "zip", "application/zip"),
    Pdf("PDF", "每页一张图，什么阅读器都能开", "pdf", "application/pdf"),
    Epub("电子书", "带目录的 epub，丢进阅读器就能看", "epub", "application/epub+zip"),
}

/**
 * 拉起系统文件选择器，把缓存的本子按选定格式写出去。
 *
 * 只有 Android 接了（SAF 保存对话框）；桌面和 iOS 的 [ExportFormat] 选项不会露出来。
 */
internal class ExportLauncher internal constructor(
    private val launchBlock: (ExportFormat, String, String) -> Unit,
) {
    fun launch(format: ExportFormat, comicId: String, title: String) =
        launchBlock(format, comicId, title)
}

/**
 * 拿一个导出器，[onResult] 在用户选完位置、文件写完（或取消）后回调。
 *
 * `ok` 为 false 且 message 为 null 表示用户取消了，不用提示错误。
 */
@Composable
internal expect fun rememberExportLauncher(
    onResult: (ok: Boolean, message: String?) -> Unit,
): ExportLauncher

/** 当前平台能不能导出缓存。 */
internal expect val exportSupported: Boolean
