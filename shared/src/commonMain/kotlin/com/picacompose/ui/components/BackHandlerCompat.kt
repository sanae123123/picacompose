package com.picacompose.ui.components

import androidx.compose.runtime.Composable

/**
 * 系统返回键。
 *
 * Android 挂到 activity 的返回回调上（预测性返回也走这里）；
 * iOS / 桌面没有返回键，先空实现，由页面自己的按钮负责退出。
 */
@Composable
expect fun BackHandlerCompat(enabled: Boolean = true, onBack: () -> Unit)
