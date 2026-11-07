package com.picacompose.picaapi

/** 当前 Unix 时间戳（秒）。各平台 actual 实现见 androidMain / jvmMain / iosMain。 */
internal expect fun nowEpochSeconds(): Long

/** 生成 32 位十六进制 nonce。 */
internal fun randomNonce(): String {
    val chars = "0123456789abcdef"
    val sb = StringBuilder(32)
    repeat(32) { sb.append(chars[kotlin.random.Random.nextInt(16)]) }
    return sb.toString()
}
