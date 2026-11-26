package com.picacompose.picaapi

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 官方的 HttpDNS：`macapi` 那几个域下的 `/app/picacomic/dns/resolve`。
 *
 * 返回的是按运营商分好的真实地址，比自己在 Cloudflare 段里采样测速准得多 —— 那边测出来的
 * 只是「能握手」，这边给的是服务端认为当前网络该用的那几台。所以它的结果垫在池子最前面，
 * 自己测的那些排在后面兜底。
 *
 * 不需要签名。两个域名轮着试，谁先给出非空结果用谁。
 */
private val MACAPI_HOSTS = listOf("macapi1.com", "macapi3.top")

/** 拿到结果就返回，拿不到就算了 —— 这只是给池子加一层，不是必须成功的依赖。 */
private const val HTTP_DNS_TIMEOUT_MS = 3_000L

private val httpDnsJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

@Serializable
private data class HttpDnsResponse(
    val code: Int = 0,
    val data: HttpDnsData? = null,
)

@Serializable
private data class HttpDnsData(
    val domain: String = "",
    /** 键是运营商（`mobile` / `unicom` / `telecom` 等），值是该运营商推荐的一组地址。 */
    val lines: Map<String, HttpDnsLine> = emptyMap(),
)

@Serializable
private data class HttpDnsLine(
    val ips: List<String> = emptyList(),
    val status: String = "",
)

private val httpDnsClient by lazy {
    HttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = HTTP_DNS_TIMEOUT_MS
            connectTimeoutMillis = HTTP_DNS_TIMEOUT_MS
        }
    }
}

/**
 * 查一次 [domain] 的地址。全失败返回空列表，调用方保留原来的池子。
 *
 * 不区分运营商：把所有线路的地址合并。设备自己报不出归属，而这几组地址指向的是同一批
 * Cloudflare anycast，混着轮换反而更容易撞上快的那条。
 */
suspend fun resolveViaHttpDns(domain: String): List<String> {
    for (host in MACAPI_HOSTS) {
        val ips = runCatching { query(host, domain) }.getOrNull().orEmpty()
        if (ips.isNotEmpty()) return ips
    }
    return emptyList()
}

private suspend fun query(host: String, domain: String): List<String> {
    val text = httpDnsClient
        .get("https://$host/app/picacomic/dns/resolve?domain=$domain")
        .bodyAsText()
    val response = httpDnsJson.decodeFromString<HttpDnsResponse>(text)
    if (response.code != 200) return emptyList()
    return response.data?.lines
        ?.filterValues { it.status.isEmpty() || it.status.equals("ok", ignoreCase = true) }
        ?.values
        ?.flatMap { it.ips }
        ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        ?.distinct()
        .orEmpty()
}
