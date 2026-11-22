package com.picacompose.data

/**
 * 桌面版没有那个本机画图引擎，磁贴照旧用包里的官方图。
 */
internal actual suspend fun aiCoverCached(art: String): ByteArray? = null

internal actual suspend fun aiCoverDraw(
    art: String,
    prompt: String,
    negative: String,
    seed: Int,
): ByteArray? = null
