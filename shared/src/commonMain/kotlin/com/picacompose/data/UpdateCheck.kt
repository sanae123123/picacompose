package com.picacompose.data

import com.picacompose.picaapi.nowEpochSeconds
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 一个新版本。 */
data class UpdateInfo(
    /** 去掉前导 v 的版本号，比如 `0.0.5`。 */
    val version: String,
    /** 更新说明，已经从 Markdown 压成纯文本。 */
    val notes: String,
    /** apk 直链（走镜像时也已经是镜像地址）。 */
    val downloadUrl: String,
    /** release 页面。 */
    val pageUrl: String,
)

/** 检查更新的结果。 */
sealed interface UpdateResult {
    /** 请求通了：有新版本就是 [info]，没有就是 null。 */
    data class Ok(val info: UpdateInfo?) : UpdateResult

    /** 直连和几个镜像都没通。 */
    data class Failed(val reason: String) : UpdateResult
}

/**
 * 检查 GitHub Release 有没有新版本。
 *
 * 顺序是「先直连 api.github.com，不通再换 gh 镜像」。国内直连 GitHub 大多不通，
 * 所以镜像不是可选项：每个镜像都套在原始地址前面（`https://ghfast.top/https://api.github.com/...`），
 * 拿到 apk 直链之后也照样套同一个前缀，后面下载也走这条路。
 */
object UpdateChecker {

    private const val REPO = "sanae123123/picacompose"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val PAGE = "https://github.com/$REPO/releases/latest"

    /** 空串是直连，剩下的是 gh 镜像。顺序按实测常用度排。 */
    private val ROUTES = listOf(
        "",
        "https://gh-proxy.com/",
        "https://ghproxy.net/",
        "https://ghfast.top/",
    )

    /** 自动检查的最短间隔：成功或失败都算一次，免得每次冷启动都把几条线路打一遍。 */
    private const val AUTO_INTERVAL_SECONDS = 3 * 3600L

    private const val KEY_LAST_CHECK = "update.lastCheckAt"
    private const val KEY_DISMISSED = "update.dismissedVersion"

    private val kv by lazy { platformKeyValueStore() }
    private val json = Json { ignoreUnknownKeys = true }

    /** 用户点过「以后再说」的版本号；同一个版本不再自动弹。 */
    var dismissedVersion: String? = null
        private set

    init {
        dismissedVersion = runCatching { kv.get(KEY_DISMISSED) }.getOrNull()
    }

    /** 记住用户不想再看的版本。 */
    fun dismiss(version: String) {
        dismissedVersion = version
        runCatching { kv.put(KEY_DISMISSED, version) }
    }

    /** 自动检查：不到间隔时间就不查，返回 null。 */
    suspend fun checkIfDue(): UpdateResult? {
        val last = runCatching { kv.get(KEY_LAST_CHECK)?.toLongOrNull() }.getOrNull() ?: 0L
        if (last > 0 && nowEpochSeconds() - last < AUTO_INTERVAL_SECONDS) return null
        return check()
    }

    /** 手动检查：无视间隔，设置页点一下就走这里。 */
    suspend fun checkNow(): UpdateResult = check()

    private suspend fun check(): UpdateResult {
        runCatching { kv.put(KEY_LAST_CHECK, nowEpochSeconds().toString()) }
        var lastError: String? = null
        for (prefix in ROUTES) {
            try {
                return UpdateResult.Ok(fetch(prefix))
            } catch (t: Throwable) {
                lastError = t.message?.takeIf { it.isNotBlank() } ?: "连不上"
            }
        }
        return UpdateResult.Failed(lastError ?: "连不上 GitHub")
    }

    /** 走一条线路抓一次 release；没有新版本返回 null。 */
    private suspend fun fetch(prefix: String): UpdateInfo? {
        val client = HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 12_000
                connectTimeoutMillis = 8_000
                socketTimeoutMillis = 12_000
            }
        }
        val body = try {
            val response = client.get(prefix + API) {
                header(HttpHeaders.UserAgent, "picacompose/$APP_VERSION")
                header(HttpHeaders.Accept, "application/vnd.github+json")
            }
            if (!response.status.isSuccess()) error("HTTP ${response.status.value}")
            response.bodyAsText()
        } finally {
            client.close()
        }

        val root = json.parseToJsonElement(body).jsonObject
        val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (tag.isEmpty()) return null
        val version = tag.removePrefix("v")
        if (!isNewerVersion(version, APP_VERSION)) return null

        val pageUrl = root["html_url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: PAGE
        val apkUrl = root["assets"]
            ?.jsonArray
            ?.mapNotNull { it.jsonObject }
            ?.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk") == true }
            ?.get("browser_download_url")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }

        return UpdateInfo(
            version = version,
            notes = plainText(root["body"]?.jsonPrimitive?.contentOrNull.orEmpty()),
            downloadUrl = prefix + (apkUrl ?: pageUrl),
            pageUrl = prefix + pageUrl,
        )
    }
}

/** [remote] 比 [current] 新就返回 true。前导 v、`-beta` 这类后缀都先剥掉。 */
fun isNewerVersion(remote: String, current: String): Boolean {
    fun parts(v: String): List<Int> = v.trim()
        .removePrefix("v")
        .substringBefore('-')
        .substringBefore('+')
        .split('.')
        .map { it.trim().toIntOrNull() ?: 0 }

    val remoteParts = parts(remote)
    val currentParts = parts(current)
    for (i in 0 until maxOf(remoteParts.size, currentParts.size)) {
        val a = remoteParts.getOrElse(i) { 0 }
        val b = currentParts.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}

/** Release 说明是 Markdown，弹窗里按纯文本显示：去掉标题符号、加粗标记和链接包装。 */
private fun plainText(markdown: String): String {
    val link = Regex("\\[([^\\]]*)]\\(([^)]*)\\)")
    return markdown
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            val bulleted = line.startsWith("- ") || line.startsWith("* ")
            var text = if (bulleted) line.substring(2) else line
            text = text
                .removePrefix("### ")
                .removePrefix("## ")
                .removePrefix("# ")
                .replace("**", "")
                .replace("`", "")
                .replace(link, "$1")
            if (bulleted) "· $text" else text
        }
        .joinToString("\n")
}
