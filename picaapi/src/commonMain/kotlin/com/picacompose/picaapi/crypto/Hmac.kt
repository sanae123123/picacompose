package com.picacompose.picaapi.crypto

/** HMAC-SHA256（RFC 2104）。 */
internal object Hmac {
    private const val BLOCK = 64
    private const val IPAD = 0x36
    private const val OPAD = 0x5c

    fun sha256(key: ByteArray, message: ByteArray): ByteArray {
        val normalized = if (key.size > BLOCK) Sha256.hash(key) else key
        val padded = ByteArray(BLOCK)
        normalized.copyInto(padded)

        val inner = ByteArray(BLOCK + message.size)
        for (i in 0 until BLOCK) inner[i] = (padded[i].toInt() xor IPAD).toByte()
        message.copyInto(inner, BLOCK)

        val innerHash = Sha256.hash(inner)

        val outer = ByteArray(BLOCK + innerHash.size)
        for (i in 0 until BLOCK) outer[i] = (padded[i].toInt() xor OPAD).toByte()
        innerHash.copyInto(outer, BLOCK)

        return Sha256.hash(outer)
    }
}

internal object Hex {
    private const val DIGITS = "0123456789abcdef"

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xff
            out.append(DIGITS[v ushr 4]).append(DIGITS[v and 0x0f])
        }
        return out.toString()
    }
}
