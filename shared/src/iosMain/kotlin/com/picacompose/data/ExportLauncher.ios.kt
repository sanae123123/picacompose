package com.picacompose.data

import androidx.compose.runtime.Composable

// iOS 还没接文档导出。
internal actual val exportSupported: Boolean = false

@Composable
internal actual fun rememberExportLauncher(
    onResult: (ok: Boolean, message: String?) -> Unit,
): ExportLauncher = ExportLauncher { _, _, _ -> }
