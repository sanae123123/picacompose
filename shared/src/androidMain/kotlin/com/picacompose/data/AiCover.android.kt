package com.picacompose.data

import android.util.Base64
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 本机画图引擎的接口（LocalDream 那类 App 在 8081 上开的服务）。 */
private const val ENGINE_URL = "http://127.0.0.1:8081/generate"

/** 引擎一次只画一张，请求排队走，别七块磁贴一起把它塞满。 */
private val engineLock = Mutex()

internal actual suspend fun aiCoverCached(art: String): ByteArray? = withContext(Dispatchers.IO) {
    val file = coverFile(art)
    if (file.isFile && file.length() > 0) runCatching { file.readBytes() }.getOrNull() else null
}

internal actual suspend fun aiCoverDraw(
    art: String,
    prompt: String,
    negative: String,
    seed: Int,
): ByteArray? {
    aiCoverCached(art)?.let { return it }
    val png = withContext(Dispatchers.IO) {
        engineLock.withLock { generate(prompt, negative, seed) }
    } ?: return null
    runCatching { coverFile(art).writeBytes(png) }
    return png
}

private fun coverDir(): File = File(picaAppContext().cacheDir, "ai-covers").apply { mkdirs() }

private fun coverFile(art: String): File {
    val safe = art.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        .take(40)
        .ifBlank { "cover" }
    return File(coverDir(), "$safe.png")
}

/** 打一发 POST，等 SSE 流走完，取里面 `complete` 事件带的那张 PNG。 */
private fun generate(prompt: String, negative: String, seed: Int): ByteArray? = try {
    val payload = buildString {
        append("{\"prompt\":").append(jsonQuote(prompt))
        append(",\"negative_prompt\":").append(jsonQuote(negative))
        append(",\"steps\":10,\"width\":512,\"height\":512")
        append(",\"seed\":").append(seed)
        append(",\"output_format\":\"png\"}")
    }
    val conn = (URL(ENGINE_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        connectTimeout = 5_000
        readTimeout = 180_000
        setRequestProperty("Content-Type", "application/json")
    }
    try {
        conn.outputStream.use { it.write(payload.toByteArray()) }
        if (conn.responseCode != 200) {
            println("AiCover: 引擎回了 ${conn.responseCode}")
            return null
        }
        val sse = conn.inputStream.use { it.readBytes().decodeToString() }
        extractPng(sse)
    } finally {
        conn.disconnect()
    }
} catch (t: Throwable) {
    // 引擎没开、被关了、画到一半挂了：都当「画不出来」，磁贴先空着。
    println("AiCover: 画不出 $seed 号：${t::class.simpleName}: ${t.message}")
    null
}

/**
 * 从 SSE 文本里抠图。进展事件是一行一个 `data: {...}`，
 * 最后一条 `"type":"complete"` 里带着整张图（base64）。
 */
private fun extractPng(sse: String): ByteArray? {
    for (line in sse.lineSequence()) {
        if (!line.startsWith("data: ")) continue
        if (!line.contains("\"type\":\"complete\"")) continue
        val base64 = IMAGE_FIELD.find(line)?.groupValues?.getOrNull(1) ?: return null
        return runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull()
    }
    println("AiCover: 流里没找到完整的图")
    return null
}

private val IMAGE_FIELD = Regex("\"image\"\\s*:\\s*\"([^\"]+)\"")

/** 塞进 JSON 字符串：引号、反斜杠和换行转义一下就够了，提示词是纯英文短句。 */
private fun jsonQuote(text: String): String = buildString {
    append('"')
    for (c in text) {
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(c)
        }
    }
    append('"')
}
