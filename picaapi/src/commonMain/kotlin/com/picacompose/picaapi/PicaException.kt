package com.picacompose.picaapi

/** API 调用失败：网络错误、非 2xx、或者业务 code 不在 2xx。 */
class PicaException(
    val httpCode: Int? = null,
    val errorCode: String? = null,
    message: String? = null,
    val rawBody: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
