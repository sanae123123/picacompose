package com.picacompose.picaapi

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 官方客户端的两个明文引导入口，只在启动时用来拉 `/init`。 */
private val BOOTSTRAP_URLS = listOf(
    "http://68.183.234.72/init",
    "http://206.189.95.169/init",
)

/**
 * `/init` 的响应体。
 *
 * 和主接口不同，它**不套** `{code, message, data}` 信封，上来就是 `status` / `addresses`。
 */
@Serializable
data class BootstrapInfo(
    val status: String = "",
    val addresses: List<String> = emptyList(),
    val message: String? = null,
    val waka: String? = null,
    @SerialName("adKeyword") val adKeyword: String? = null,
)

private val bootstrapJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private val bootstrapClient by lazy {
    HttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 4_000
            connectTimeoutMillis = 3_000
        }
    }
}

/**
 * 拉一次线路列表：两个引导 IP 依次试，谁先给出 `status=OK` 就用谁。
 *
 * 这里必须走**明文 HTTP** —— 官方就是这么设计的，域名被污染或证书链异常时它是唯一能通的入口。
 * 全失败返回 null，调用方回退到线路 1（系统解析）。
 */
suspend fun fetchBootstrap(): BootstrapInfo? {
    for (url in BOOTSTRAP_URLS) {
        val text = runCatching { bootstrapClient.get(url).bodyAsText() }.getOrNull() ?: continue
        val info = runCatching { bootstrapJson.decodeFromString<BootstrapInfo>(text) }.getOrNull() ?: continue
        if (info.status.equals("OK", ignoreCase = true)) return info
    }
    return null
}

/**
 * 建一个「DNS 钉到指定 IP 池」的引擎，对应官方客户端的 `KEY_IS_GIRL` 分流。
 *
 * 域名仍然是 `picaapi.picacomic.com`（签名和证书都按它算），只有 TCP 目标换成下发 IP。
 * 平台没有 DNS 钩子时返回 null，调用方回退系统解析。
 */
expect suspend fun pinnedEngine(ips: List<String>): HttpClientEngine?
