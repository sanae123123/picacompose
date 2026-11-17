package com.picacompose.data

import androidx.compose.runtime.Composable

// 桌面端没接文件保存对话框，导出入口先不摆。
internal actual val exportSupported: Boolean = false

@Composable
internal actual fun rememberExportLauncher(
    onResult: (ok: Boolean, message: String?) -> Unit,
): ExportLauncher = ExportLauncher { _, _, _ -> }
