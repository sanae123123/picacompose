package com.picacompose.ui.components

import androidx.compose.runtime.Composable

/**
 * 从系统相册挑一张图。
 *
 * 挑好、缩到头像够用的尺寸再回调 JPEG 字节；用户取消就不回调。
 * 不支持相册的平台 [imagePickerSupported] 是 false，界面那边据此决定要不要露出入口，
 * 免得点了没反应。
 */
@Composable
expect fun rememberImagePicker(onPicked: (ByteArray) -> Unit): () -> Unit

/** 当前平台有没有实现相册选择。 */
expect val imagePickerSupported: Boolean
