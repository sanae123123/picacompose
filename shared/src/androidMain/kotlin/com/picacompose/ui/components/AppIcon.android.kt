package com.picacompose.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext

/** 取 192px 版本够 46dp 的图标用了（xxhdpi 下 138px）。 */
private const val ICON_PX = 192

@Composable
actual fun rememberAppIcon(): ImageBitmap? {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            // 自适应图标也走这里：PackageManager 给的是已经套好背景的那一张。
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, ICON_PX, ICON_PX)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        }.getOrNull()
    }
}
