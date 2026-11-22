package com.picacompose.data

/**
 * Android 和桌面都是 JVM，从 classpath 读。
 *
 * 两边的资源目录都指向仓库根的 `help/`（见 shared/build.gradle.kts），
 * 所以这里直接按 `/help/<名字>` 取。
 */
internal actual fun readHelpFile(name: String): ByteArray? =
    HelpPages::class.java.getResourceAsStream("/help/$name")?.use { it.readBytes() }
