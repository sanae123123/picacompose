package com.picacompose.picaapi

import com.picacompose.picaapi.crypto.Hex
import com.picacompose.picaapi.crypto.Hmac
import com.picacompose.picaapi.crypto.Sha256
import kotlin.test.Test
import kotlin.test.assertEquals

class CryptoTest {

    @Test
    fun sha256MatchesKnownVectors() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Hex.encode(Sha256.hash(ByteArray(0))),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Hex.encode(Sha256.hash("abc".encodeToByteArray())),
        )
        // 跨 block 的消息（超过 64 字节）
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Hex.encode(Sha256.hash("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray())),
        )
    }

    @Test
    fun hmacMatchesRfc4231Case2() {
        assertEquals(
            "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
            Hex.encode(
                Hmac.sha256(
                    "Jefe".encodeToByteArray(),
                    "what do ya want for nothing?".encodeToByteArray(),
                ),
            ),
        )
    }

    @Test
    fun signatureMatchesReferenceImplementation() {
        val signature = PicaSignature.sign(
            path = "comics",
            time = "1700000000",
            nonce = "0123456789abcdef0123456789abcdef",
            method = "GET",
            apiKey = PicaConfig.DEFAULT_API_KEY,
            hmacKey = PicaConfig.DEFAULT_HMAC_KEY,
        )
        assertEquals("f6ba20199308e1c61bb785f00965fb6ae96c57991c8608d38120e36dd36d6bd2", signature)
    }
}
