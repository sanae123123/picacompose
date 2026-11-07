package com.picacompose.picaapi.crypto

/**
 * 纯 Kotlin 的 SHA-256，避免为签名引平台相关加密库（Android/JVM 用 MessageDigest，
 * iOS 用 CommonCrypto，需要 expect/actual，还会破坏 testability）。
 */
internal object Sha256 {
    private const val BLOCK = 64

    private val K = intArrayOf(
        0x428a2f98.toInt(), 0x71374491.toInt(), 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(), 0x3956c25b.toInt(), 0x59f111f1.toInt(), 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01.toInt(), 0x243185be.toInt(), 0x550c7dc3.toInt(), 0x72be5d74.toInt(), 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6.toInt(), 0x240ca1cc.toInt(), 0x2de92c6f.toInt(), 0x4a7484aa.toInt(), 0x5cb0a9dc.toInt(), 0x76f988da.toInt(),
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(), 0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351.toInt(), 0x14292967.toInt(),
        0x27b70a85.toInt(), 0x2e1b2138.toInt(), 0x4d2c6dfc.toInt(), 0x53380d13.toInt(), 0x650a7354.toInt(), 0x766a0abb.toInt(), 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(), 0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070.toInt(),
        0x19a4c116.toInt(), 0x1e376c08.toInt(), 0x2748774c.toInt(), 0x34b0bcb5.toInt(), 0x391c0cb3.toInt(), 0x4ed8aa4a.toInt(), 0x5b9cca4f.toInt(), 0x682e6ff3.toInt(),
        0x748f82ee.toInt(), 0x78a5636f.toInt(), 0x84c87814.toInt(), 0x8cc70208.toInt(), 0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt()
    )

    private val H0 = intArrayOf(
        0x6a09e667.toInt(), 0xbb67ae85.toInt(), 0x3c6ef372.toInt(), 0xa54ff53a.toInt(),
        0x510e527f.toInt(), 0x9b05688c.toInt(), 0x1f83d9ab.toInt(), 0x5be0cd19.toInt()
    )

    fun hash(message: ByteArray): ByteArray {
        val zeros = (56 - (message.size + 1) % BLOCK + BLOCK) % BLOCK
        val padded = ByteArray(message.size + 1 + zeros + 8)
        message.copyInto(padded)
        padded[message.size] = 0x80.toByte()
        val bitLen = message.size.toLong() * 8
        for (i in 0 until 8) {
            padded[padded.size - 8 + i] = ((bitLen ushr (56 - 8 * i)) and 0xff).toByte()
        }

        val h = H0.copyOf()
        val w = IntArray(64)
        var offset = 0
        while (offset < padded.size) {
            for (i in 0 until 16) {
                val p = offset + i * 4
                w[i] = ((padded[p].toInt() and 0xff) shl 24) or
                    ((padded[p + 1].toInt() and 0xff) shl 16) or
                    ((padded[p + 2].toInt() and 0xff) shl 8) or
                    (padded[p + 3].toInt() and 0xff)
            }
            for (i in 16 until 64) {
                val x = w[i - 15]
                val y = w[i - 2]
                val s0 = x.rotr(7) xor x.rotr(18) xor (x ushr 3)
                val s1 = y.rotr(17) xor y.rotr(19) xor (y ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }

            var a = h[0]
            var b = h[1]
            var c = h[2]
            var d = h[3]
            var e = h[4]
            var f = h[5]
            var g = h[6]
            var hh = h[7]

            for (i in 0 until 64) {
                val s1 = e.rotr(6) xor e.rotr(11) xor e.rotr(25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = hh + s1 + ch + K[i] + w[i]
                val s0 = a.rotr(2) xor a.rotr(13) xor a.rotr(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj
                hh = g
                g = f
                f = e
                e = d + t1
                d = c
                c = b
                b = a
                a = t1 + t2
            }

            h[0] += a
            h[1] += b
            h[2] += c
            h[3] += d
            h[4] += e
            h[5] += f
            h[6] += g
            h[7] += hh
            offset += BLOCK
        }

        val out = ByteArray(32)
        for (i in 0 until 8) {
            out[i * 4] = (h[i] ushr 24).toByte()
            out[i * 4 + 1] = (h[i] ushr 16).toByte()
            out[i * 4 + 2] = (h[i] ushr 8).toByte()
            out[i * 4 + 3] = h[i].toByte()
        }
        return out
    }

    private fun Int.rotr(bits: Int): Int = (this ushr bits) or (this shl (32 - bits))
}
