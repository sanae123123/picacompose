package com.picacompose.data

/**
 * 读取打进包里的帮助页文件（`help/` 目录）。
 *
 * 页面和配图都当资源带着走：Android 从 APK、桌面从 jar 里读，iOS 待补（见 ios 的实现）。
 */
internal expect fun readHelpFile(name: String): ByteArray?
