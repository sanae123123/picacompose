package com.picacompose.data

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
 * 导出进度：前台那个弹窗和通知栏看的是同一份。
 *
 * [fraction] 给进度条用；[detail] 是一句人话，比如「第 12/200 页」或「45.6 MB / 120.3 MB」。
 */
internal data class ExportProgress(
    val title: String,
    val formatLabel: String,
    val fraction: Float,
    val detail: String,
    /** 用户点了「后台执行」，弹窗收起、进度改走通知栏。 */
    val background: Boolean = false,
    val finished: Boolean = false,
    val error: String? = null,
)

/** 当前这次导出的进度，没有导出时为 null。 */
internal object ExportProgressStore {

    private val _state = MutableStateFlow<ExportProgress?>(null)

    val state: StateFlow<ExportProgress?> = _state.asStateFlow()

    fun set(progress: ExportProgress?) {
        _state.value = progress
    }

    fun update(transform: (ExportProgress?) -> ExportProgress?) {
        _state.update(transform)
    }
}

/**
 * 拉起系统文件选择器，把缓存的本子按选定格式写出去。
 *
 * 只有 Android 接了（SAF 保存对话框）；桌面和 iOS 的导出入口不会露出来。
 */
internal class ExportLauncher internal constructor(
    private val startBlock: (ExportFormat, String, String) -> Unit,
    private val backgroundBlock: () -> Unit,
    private val cancelBlock: () -> Unit,
) {
    fun launch(format: ExportFormat, comicId: String, title: String) =
        startBlock(format, comicId, title)

    /** 弹窗收起来，导出接着跑，进度显示到通知栏。 */
    fun moveToBackground() = backgroundBlock()

    fun cancel() = cancelBlock()
}

@Composable
internal expect fun rememberExportLauncher(): ExportLauncher

/** 当前平台能不能导出缓存。 */
internal expect val exportSupported: Boolean
