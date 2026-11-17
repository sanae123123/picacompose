package com.picacompose.picaapi

/** 图片下载失败：`httpCode` 为服务端返回的状态码，网络层直接失败时为 null。 */
class PicaImageException(
    val httpCode: Int?,
    val url: String,
    cause: Throwable? = null,
) : Exception("图片下载失败（${httpCode ?: "网络错误"}）：$url", cause) {

    /** 4xx（429 限流除外）一般是「这张图本来就不在」，重试没意义。 */
    val permanent: Boolean
        get() = httpCode != null && httpCode in 400..499 && httpCode != 429
}
