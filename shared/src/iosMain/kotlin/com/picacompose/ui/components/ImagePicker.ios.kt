package com.picacompose.ui.components

import androidx.compose.runtime.Composable

// iOS 还没接 PHPicker：先如实说没有，界面那边就不摆「更换头像」了。
actual val imagePickerSupported: Boolean = false

@Composable
actual fun rememberImagePicker(onPicked: (ByteArray) -> Unit): () -> Unit = {}
