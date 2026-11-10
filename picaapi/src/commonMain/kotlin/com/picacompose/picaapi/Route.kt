package com.picacompose.picaapi

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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

/** DoH 端点：按「结果更可信」的顺序排，国内的放后面当兜底。 */
private val DOH_ENDPOINTS = listOf(
    "https://1.1.1.1/dns-query",
    "https://8.8.8.8/dns-query",
    "https://223.5.5.5/dns-query",
    "https://1.12.12.12/dns-query",
)

private val dohClient by lazy {
    HttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 4_000
            connectTimeoutMillis = 3_500
        }
    }
}

/** 接口域名，DoH 默认解析这个。 */
private const val DEFAULT_API_HOST = "picaapi.picacomic.com"

/**
 * 用 DoH 解析 [host] 的 A 记录，全都不通就返回空列表。
 *
 * 端点写的是 IP 直连：解析器自己也得先解析一遍就成死循环了，好在这些证书里带 IP，
 * 用 IP 连照样过校验。四个端点一起发出去，按 [DOH_ENDPOINTS] 的顺序取第一个答上来的，
 * 所以最坏也就等一个超时，不会一个个排队等。
 *
 * 国内那两个解析器对某些域名给的地址是被污染过的，所以排在后面，只有前面都连不上才用。
 */
@OptIn(ExperimentalEncodingApi::class)
suspend fun dohAddresses(host: String = DEFAULT_API_HOST): List<String> = coroutineScope {
    val query = Base64.UrlSafe.encode(dnsQueryPacket(host))
    val jobs = DOH_ENDPOINTS.map { endpoint ->
        async { runCatching { dohLookup(endpoint, query) }.getOrDefault(emptyList()) }
    }
    jobs.map { it.await() }.firstOrNull { it.isNotEmpty() }.orEmpty()
}

private suspend fun dohLookup(endpoint: String, encodedQuery: String): List<String> {
    val response = dohClient.get("$endpoint?dns=$encodedQuery") {
        header("accept", "application/dns-message")
    }
    if (response.status.value != 200) return emptyList()
    return parseAnswers(response.readBytes())
}

/** 手搓一个 A 记录的查询包：12 字节头 + 域名 + 类型/类。 */
private fun dnsQueryPacket(host: String): ByteArray {
    val bytes = mutableListOf<Byte>()
    fun short(value: Int) {
        bytes += (value shr 8 and 0xFF).toByte()
        bytes += (value and 0xFF).toByte()
    }
    short(0)        // 事务 id，随便
    short(0x0100)   // 标准查询 + 要递归
    short(1)        // 一个问题
    short(0)        // 答案 / 授权 / 附加都留空
    short(0)
    short(0)
    host.trim('.').split('.').forEach { label ->
        bytes += label.length.toByte()
        label.encodeToByteArray().forEach { bytes += it }
    }
    bytes += 0
    short(1)        // A
    short(1)        // IN
    return bytes.toByteArray()
}

/**
 * 从响应里挖出 A 记录。
 *
 * 名字可能被压缩（高两位 11 的指针），所以跳名字要单独写；挖不到的字段一律当没有。
 */
private fun parseAnswers(buf: ByteArray): List<String> {
    if (buf.size < 12) return emptyList()

    fun u16(at: Int): Int = ((buf[at].toInt() and 0xFF) shl 8) or (buf[at + 1].toInt() and 0xFF)

    fun skipName(from: Int): Int {
        var at = from
        while (at < buf.size) {
            val length = buf[at].toInt() and 0xFF
            if (length == 0) return at + 1
            if (length and 0xC0 == 0xC0) return at + 2
            at += 1 + length
        }
        return at
    }

    var offset = 12
    repeat(u16(4)) { offset = skipName(offset) + 4 }

    val ips = mutableListOf<String>()
    repeat(u16(6)) {
        if (offset >= buf.size) return@repeat
        offset = skipName(offset)
        if (offset + 10 > buf.size) return@repeat
        val type = u16(offset)
        val dataLength = u16(offset + 8)
        offset += 10
        if (offset + dataLength > buf.size) return@repeat
        if (type == 1 && dataLength == 4) {
            ips += listOf(buf[offset], buf[offset + 1], buf[offset + 2], buf[offset + 3])
                .joinToString(".") { (it.toInt() and 0xFF).toString() }
        }
        offset += dataLength
    }
    return ips
}
