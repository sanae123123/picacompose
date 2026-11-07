package com.picacompose.picaapi

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 临时诊断：确认常量字面量在运行时的真实字节，以及用显式 key 算出的签名。
 */
class DiagnosticsTest {

    private val expectedKeyCodes = intArrayOf(
        126, 100, 125, 81, 55, 36, 101, 73, 110, 105, 61, 86, 41, 57, 92, 82, 75, 47, 80, 46, 82, 77, 52, 59, 57, 91, 55, 124, 64, 47, 67, 65, 125, 98, 126, 79, 87, 33, 51, 63, 69, 86, 96, 58, 60, 62, 77, 55, 112, 100, 100, 85, 66, 76, 53, 110, 124, 48, 47, 42, 67, 110
    )

    @Test
    fun hmacKeyLiteralDecodesAsExpected() {
        val actual = PicaConfig.DEFAULT_HMAC_KEY.map { it.code }
        assertEquals(expectedKeyCodes.toList().size, actual.size, "length")
        assertEquals(expectedKeyCodes.toList(), actual)
    }

    @Test
    fun apiKeyLiteral() {
        assertEquals("C69BAF41DA5ABD1FFEDC6D2FEA56B", PicaConfig.DEFAULT_API_KEY)
    }

    @Test
    fun signatureWithExplicitKeyBytes() {
        val key = expectedKeyCodes.map { it.toChar() }.joinToString("")
        val raw = ("comics" + "1700000000" + "0123456789abcdef0123456789abcdef" + "GET" +
            "C69BAF41DA5ABD1FFEDC6D2FEA56B").lowercase()
        assertEquals(
            "749853d3aa994949cfdb84b44c4dce8e36da348c0bd2af24a6dbf3bd9824d6c5",
            PicaSignature.hmacSha256(raw, key),
        )
    }
}
