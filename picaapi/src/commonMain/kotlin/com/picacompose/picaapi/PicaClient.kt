package com.picacompose.picaapi

import com.picacompose.picaapi.api.AuthApi
import com.picacompose.picaapi.api.ComicApi
import com.picacompose.picaapi.api.CommentApi
import com.picacompose.picaapi.api.EpisodeApi
import com.picacompose.picaapi.api.MetaApi
import com.picacompose.picaapi.api.UserApi
import com.picacompose.picaapi.model.Envelope
import com.picacompose.picaapi.model.Thumbnail
import com.picacompose.picaapi.model.UserProfile
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * PicACG 跨平台客户端（Android / JVM / iOS 共用，Ktor 3 + kotlinx.serialization）。
 *
 * 参考实现：
 * - czp3009/picacomic-api（ktor 1.x，JVM）
 * - qiunanrima/PicACG-Api-Java（OkHttp + Gson，JVM）
 *
 * ```kotlin
 * val pica = PicaClient()
 * pica.auth.signIn("a@b.com", "pw")
 * val page = pica.comics.list(page = 1, category = "全彩")
 * val detail = pica.comics.detail(page.docs.first().id)
 * ```
 */
class PicaClient(
    val config: PicaConfig = PicaConfig(),
    engine: HttpClientEngine? = null,
) {
    internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
        encodeDefaults = true
    }

    private val timeOffset = MutableStateFlow(config.timeOffsetSeconds)

    /** `init` 接口会下发图片服务器，官方 App 也是拿到后覆盖本地配置。 */
    private val imageServer = MutableStateFlow(config.imageServer)

    /** 鉴权令牌；登录成功后自动写入，也会作为 `authorization` 头发送。 */
    val token = MutableStateFlow(config.authorization)

    private val http: HttpClient = if (engine != null) {
        HttpClient(engine) { configure() }
    } else {
        HttpClient { configure() }
    }

    private fun HttpClientConfig<*>.configure() {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
    }

    val auth: AuthApi get() = authApi
    val comics: ComicApi get() = comicApi
    val episodes: EpisodeApi get() = episodeApi
    val comments: CommentApi get() = commentApi
    val users: UserApi get() = userApi
    val meta: MetaApi get() = metaApi

    private val authApi = AuthApi(this)
    private val comicApi = ComicApi(this)
    private val episodeApi = EpisodeApi(this)
    private val commentApi = CommentApi(this)
    private val userApi = UserApi(this)
    private val metaApi = MetaApi(this)

    /** 图片地址拼接，自动带上配置里的 imageServer。 */
    fun imageUrl(thumbnail: Thumbnail?): String? = PicaImage.url(thumbnail, imageServer.value)

    fun avatarUrl(profile: UserProfile?): String? = PicaImage.avatarUrl(profile, imageServer.value)

    internal fun updateImageServer(value: String?) {
        if (!value.isNullOrBlank()) imageServer.value = value
    }

    fun updateToken(value: String?) {
        token.value = value
    }

    /** 当前已知的服务器时间偏移（秒）。 */
    val serverTimeOffsetSeconds: Long get() = timeOffset.value

    internal fun signedTime(): String = (nowEpochSeconds() + timeOffset.value).toString()

    /** 拼出带 query 的完整 URL；返回的字符串同时用于签名，避免编码方式不一致。 */
    internal fun buildUrl(path: String, query: Map<String, String?> = emptyMap()): String {
        val builder = URLBuilder()
        builder.takeFrom(config.normalizedBaseUrl() + path.trimStart('/'))
        query.forEach { (key, value) ->
            if (value != null) builder.parameters.append(key, value)
        }
        return builder.build().toString()
    }

    /** 发请求 → 校验 HTTP 状态 → 解信封 → 返回 data。 */
    internal suspend fun <T> call(
        method: HttpMethod,
        path: String,
        query: Map<String, String?> = emptyMap(),
        bodyJson: String? = null,
        authorized: Boolean = true,
        serializer: KSerializer<T>,
    ): T {
        val response = send(method, path, query, bodyJson, authorized)
        val httpCode = response.status.value
        val text = response.bodyAsText()
        if (httpCode !in 200..299) throw errorFrom(httpCode, text)

        val envelope = json.decodeFromString(Envelope.serializer(serializer), text)
        if (envelope.code !in 200..299) {
            throw PicaException(httpCode, envelope.code.toString(), envelope.message, text)
        }
        return envelope.data
            ?: throw PicaException(httpCode, envelope.code.toString(), envelope.message ?: "响应中没有 data", text)
    }

    /**
     * 发请求并返回整个 JSON 体（不拆信封）。
     *
     * 给 `auth/register` 这种响应结构不完全统一、或者暂时没有建模型的接口用。
     */
    internal suspend fun callRaw(
        method: HttpMethod,
        path: String,
        query: Map<String, String?> = emptyMap(),
        bodyJson: String? = null,
        authorized: Boolean = true,
    ): JsonElement {
        val response = send(method, path, query, bodyJson, authorized)
        val httpCode = response.status.value
        val text = response.bodyAsText()
        if (httpCode !in 200..299) throw errorFrom(httpCode, text)
        return runCatching { json.parseToJsonElement(text) }.getOrDefault(JsonNull)
    }

    /** 只关心成功与否的接口（点赞、收藏、评论等）。 */
    internal suspend fun callWithoutResult(
        method: HttpMethod,
        path: String,
        query: Map<String, String?> = emptyMap(),
        bodyJson: String? = null,
        authorized: Boolean = true,
    ) {
        val response = send(method, path, query, bodyJson, authorized)
        val httpCode = response.status.value
        val text = response.bodyAsText()
        if (httpCode !in 200..299) throw errorFrom(httpCode, text)
        if (!looksLikeEnvelope(text)) return

        val envelope = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        val code = envelope["code"]?.jsonPrimitive?.intOrNull ?: return
        if (code !in 200..299) {
            throw PicaException(
                httpCode = httpCode,
                errorCode = code.toString(),
                message = envelope["message"]?.jsonPrimitive?.contentOrNull,
                rawBody = text,
            )
        }
    }

    private suspend fun send(
        method: HttpMethod,
        path: String,
        query: Map<String, String?>,
        bodyJson: String?,
        authorized: Boolean,
    ) = runCatching {
        val url = buildUrl(path, query)
        val signaturePath = url.removePrefix(config.normalizedBaseUrl())
        val time = signedTime()
        val nonce = randomNonce()
        val signature = PicaSignature.sign(
            path = signaturePath,
            time = time,
            nonce = nonce,
            method = method.value,
            apiKey = config.apiKey,
            hmacKey = config.hmacKey,
        )

        val response = http.request(url) {
            this.method = method
            header("api-key", config.apiKey)
            header("accept", PicaConfig.ACCEPT)
            header("app-channel", config.appChannel.toString())
            header("time", time)
            header("nonce", nonce)
            header("signature", signature)
            header("app-version", config.appVersion)
            header("app-uuid", config.appUuid)
            header("image-quality", config.imageQuality)
            header("app-platform", "android")
            header("app-build-version", config.appBuildVersion)
            header("User-Agent", config.userAgent)
            if (authorized) {
                token.value?.takeIf { it.isNotBlank() }?.let { header("authorization", it) }
            }
            // 服务端对 charset 大小写敏感：必须是 `UTF-8`，写成小写 `utf-8` 会让 POST 接口
            // 返回 400/1023 "too many requests"（登录会被误判成限流）。
            if (bodyJson != null) {
                header(HttpHeaders.ContentType, PicaConfig.JSON_CONTENT_TYPE)
                setBody(bodyJson)
            } else if (method == HttpMethod.Post || method == HttpMethod.Put) {
                // 官方客户端（OkHttp）对空 body 的 POST 也会带 JSON content-type
                header(HttpHeaders.ContentType, PicaConfig.JSON_CONTENT_TYPE)
                setBody("")
            }
        }
        response.headers["Server-Time"]?.trim()?.toLongOrNull()?.let { serverTime ->
            timeOffset.value = serverTime - nowEpochSeconds()
        }
        response
    }.getOrElse { throw PicaException(message = it.message, cause = it) }

    private fun looksLikeEnvelope(text: String): Boolean =
        text.trimStart().startsWith('{')

    private fun errorFrom(httpCode: Int, text: String): PicaException {
        val payload = runCatching { json.decodeFromString(ErrorPayload.serializer(), text) }.getOrNull()
        return PicaException(
            httpCode = httpCode,
            errorCode = payload?.error ?: payload?.code,
            message = payload?.message ?: payload?.detail,
            rawBody = text,
        )
    }

    /** 关闭底层 HttpClient。 */
    fun close() {
        http.close()
    }

    @kotlinx.serialization.Serializable
    private data class ErrorPayload(
        val error: String? = null,
        val message: String? = null,
        val detail: String? = null,
        val code: String? = null,
    )
}
