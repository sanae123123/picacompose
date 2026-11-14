package com.picacompose.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * 宿主应用的图标，Android 上就是启动器里那个。
 *
 * 登录页左上角原来画的是一个渐变方块加字母 P，跟装到桌面上的图标对不上；
 * 这里直接把真正的应用图标取出来用，各端一眼就能对上。
 *
 * 桌面 / iOS 上没有「已安装的应用图标」这回事，返回 null，调用方退回自绘的字标。
 */
@Composable
expect fun rememberAppIcon(): ImageBitmap?
