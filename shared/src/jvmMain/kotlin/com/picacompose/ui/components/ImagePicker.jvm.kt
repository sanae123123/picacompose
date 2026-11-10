package com.picacompose.ui.components

import androidx.compose.runtime.Composable

// 桌面端没接文件选择框，先如实说没有。
actual val imagePickerSupported: Boolean = false

@Composable
actual fun rememberImagePicker(onPicked: (ByteArray) -> Unit): () -> Unit = {}
