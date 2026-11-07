package com.picacompose.picaapi

import com.picacompose.picaapi.crypto.Hex
import com.picacompose.picaapi.crypto.Hmac

/**
 * PicACG 请求签名。
 *
 * 原文：`(path + time + nonce + method + apiKey).lowercase()`
 * 结果：`hex(HMAC-SHA256(原文, hmacKey))`
 *
 * `path` 是去掉 baseUrl 后的完整路径，**包含 query**，且不带前导斜杠；
 * 例如 `comics?page=1&c=%E5%85%A8%E5%BD%A9`。
 */
object PicaSignature {

    fun sign(
        path: String,
        time: String,
        nonce: String,
        method: String,
        apiKey: String,
        hmacKey: String,
    ): String {
        val raw = (path + time + nonce + method + apiKey).lowercase()
        return hmacSha256(raw, hmacKey)
    }

    fun hmacSha256(raw: String, key: String): String =
        Hex.encode(Hmac.sha256(key.encodeToByteArray(), raw.encodeToByteArray()))
}
