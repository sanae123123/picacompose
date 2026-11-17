package com.picacompose.data

/** 字节数转成人看的大小：`45.6 MB` 这种。 */
internal fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${(kb * 10).toInt() / 10.0} KB"
    val mb = kb / 1024.0
    if (mb < 1024) return "${(mb * 10).toInt() / 10.0} MB"
    return "${(mb / 1024.0 * 10).toInt() / 10.0} GB"
}

/** 速度：`1.2 MB/s`。 */
internal fun formatSpeed(bytesPerSecond: Long): String = "${formatSize(bytesPerSecond)}/s"
