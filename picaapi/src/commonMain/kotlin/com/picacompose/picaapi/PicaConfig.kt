package com.picacompose.picaapi

/**
 * PicACG 客户端配置。默认值与官方 Android 客户端一致，直接用默认值即可被服务端接受。
 */
data class PicaConfig(
    /** API 根地址，必须以 `/` 结尾。 */
    val baseUrl: String = DEFAULT_BASE_URL,
    /** `api-key` 头，同时参与签名。 */
    val apiKey: String = DEFAULT_API_KEY,
    /** 签名用的 HMAC-SHA256 密钥（Key2）。 */
    val hmacKey: String = DEFAULT_HMAC_KEY,
    val appVersion: String = DEFAULT_APP_VERSION,
    val appBuildVersion: String = DEFAULT_APP_BUILD_VERSION,
    /** `app-uuid` 头，同一安装应保持稳定。 */
    val appUuid: String = DEFAULT_APP_UUID,
    val appChannel: Int = DEFAULT_APP_CHANNEL,
    /** `image-quality` 头：original / low / medium / high。 */
    val imageQuality: String = DEFAULT_IMAGE_QUALITY,
    val userAgent: String = DEFAULT_USER_AGENT,
    /** 已登录令牌；登录成功后客户端会自动写入。 */
    val authorization: String? = null,
    /** 本地时钟相对服务器的偏移（秒），由客户端收到 `Server-Time` 头后自动校正。 */
    val timeOffsetSeconds: Long = 0L,
    /** 图片 CDN 前缀；为空时用响应里的 fileServer。 */
    val imageServer: String? = null,
) {
    fun normalizedBaseUrl(): String = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

    companion object {
        const val DEFAULT_BASE_URL = "https://picaapi.picacomic.com/"
        const val DEFAULT_API_KEY = "C69BAF41DA5ABD1FFEDC6D2FEA56B"

        // 与官方客户端 getStringSigFromNative() 一致的 Key2
        const val DEFAULT_HMAC_KEY =
            "~d}\$Q7\$eIni=V)9\\RK/P.RM4;9[7|@/CA}b~OW!3?EV`:<>M7pddUBL5n|0/*Cn"

        const val DEFAULT_APP_VERSION = "2.2.1.3.3.4"
        const val DEFAULT_APP_BUILD_VERSION = "45"
        const val DEFAULT_APP_CHANNEL = 1
        const val DEFAULT_IMAGE_QUALITY = "medium"
        const val DEFAULT_USER_AGENT = "okhttp/3.8.1"
        const val DEFAULT_APP_UUID = "defaultUuid"
        const val ACCEPT = "application/vnd.picacomic.com.v1+json"

        /**
         * POST/PUT 的 Content-Type。charset 必须是大写 `UTF-8`：
         * 服务端对大小写敏感，小写 `utf-8` 或缺 charset 会返回 400/1023 "too many requests"。
         */
        const val JSON_CONTENT_TYPE = "application/json; charset=UTF-8"
    }
}
