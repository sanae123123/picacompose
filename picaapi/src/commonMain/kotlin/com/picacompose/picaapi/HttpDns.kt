package com.picacompose.picaapi

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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

/**
 * 单次最多等这么久。实测服务端首次要现查一遍，`macapi3.top` 回答要 6 秒多，
 * 网络差的时候更久，这里给得比实测值宽很多。反正跑在后台，慢一点不卡界面。
 */
private const val HTTP_DNS_TIMEOUT_MS = 15_000L

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
 * 两个域名**并发**问：其中一个在国内实测要 6 秒多（服务端首次得现解析），串行等就太久了。
 * 谁先给出非空结果用谁，另一个当场取消。
 *
 * 不区分运营商：把所有线路的地址合并。设备自己报不出归属，而这几组地址指向的是同一批
 * Cloudflare anycast，混着轮换反而更容易撞上快的那条。
 */
suspend fun resolveViaHttpDns(domain: String): List<String> = coroutineScope {
    val answers = Channel<List<String>>(Channel.UNLIMITED)
    val jobs = MACAPI_HOSTS.map { host ->
        launch {
            answers.trySend(runCatching { query(host, domain) }.getOrDefault(emptyList()))
        }
    }
    var found: List<String> = emptyList()
    for (index in MACAPI_HOSTS.indices) {
        val ips = answers.receive()
        if (ips.isNotEmpty()) {
            found = ips
            break
        }
    }
    // 已经有结果（或全部失败）就把剩下的请求停掉，不用再等。
    jobs.forEach { it.cancel() }
    found
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
