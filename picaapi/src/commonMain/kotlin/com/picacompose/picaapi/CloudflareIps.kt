package com.picacompose.picaapi

/**
 * Cloudflare 官方公布的地址段（`cloudflare.com/ips-v4`、`ips-v6` 那份）。
 *
 * 这里只是把段列出来，真正测速的是后台任务：段里均匀取样、逐台握手，挑延迟最低的那几台。
 * 不用写死某几台 —— Cloudflare 是 anycast，段里任何一台都能接 picacomic 的请求。
 */
object CloudflareIps {

    /** 官方 IPv4 段。 */
    private val V4 = listOf(
        "173.245.48.0/20",
        "103.21.244.0/22",
        "103.22.200.0/22",
        "103.31.4.0/22",
        "141.101.64.0/18",
        "108.162.192.0/18",
        "190.93.240.0/20",
        "188.114.96.0/20",
        "197.234.240.0/22",
        "198.41.128.0/17",
        "162.158.0.0/15",
        "104.16.0.0/13",
        "104.24.0.0/14",
        "172.64.0.0/13",
        "131.0.72.0/22",
    )

    /** 官方 IPv6 段。 */
    private val V6 = listOf(
        "2400:cb00::/32",
        "2606:4700::/32",
        "2803:f800::/32",
        "2405:b500::/32",
        "2405:8100::/32",
        "2a06:98c0::/29",
        "2c0f:f248::/32",
    )

    /**
     * 从全部官方段里均匀取样，凑出 [budget] 台左右的候选地址。
     *
     * 段越大分得越多台，这样每个段都被覆盖到；取样点均匀铺开，不会只测段的开头几台。
     */
    fun sample(budget: Int = 220): List<String> {
        val v4 = V4.map { it to (32 - it.substringAfter('/').toInt() + 1) }
        val v6 = V6.map { it to 8 }
        val total = (v4 + v6).sumOf { it.second }.toDouble()
        val out = mutableListOf<String>()
        for ((prefix, weight) in v4) {
            val count = (budget * weight / total).toInt().coerceIn(4, 48)
            out += sampleV4(prefix, count)
        }
        for ((prefix, weight) in v6) {
            val count = (budget * weight / total).toInt().coerceIn(4, 16)
            out += sampleV6(prefix, count)
        }
        return out.distinct()
    }

    /** 在 [cidr] 里均匀取 [count] 个点，跳过网络地址本身。 */
    private fun sampleV4(cidr: String, count: Int): List<String> {
        val base = parseV4(cidr.substringBefore('/'))
        val hostBits = 32 - cidr.substringAfter('/').toInt()
        val size = 1L shl hostBits
        val step = (size / count).coerceAtLeast(1)
        return (0 until count).map { i -> formatV4(base + ((i * step + 1) % size)) }
    }

    /**
     * IPv6 只动低 64 位：高 64 位是前缀，保持不动，低 64 位均匀铺开。
     *
     * 官方段至少是 /32，所以低 64 位完全在段内；这样不用搞 128 位大数运算也能覆盖整段。
     */
    private fun sampleV6(cidr: String, count: Int): List<String> {
        val (hi, lo) = parseV6(cidr.substringBefore('/'))
        val step = if (count <= 1) 0uL else ULong.MAX_VALUE / (count - 1).toULong()
        return (0 until count).map { i ->
            val offset = step * i.toULong()
            formatV6(hi, lo + offset)
        }
    }

    private fun parseV4(text: String): Long =
        text.split('.').fold(0L) { acc, part -> (acc shl 8) or part.toLong() }

    private fun formatV4(value: Long): String =
        listOf(24, 16, 8, 0).joinToString(".") { shift -> ((value shr shift) and 0xFF).toString() }

    private fun parseV6(text: String): Pair<ULong, ULong> {
        val halves = text.split("::")
        val head = halves[0].takeIf { it.isNotEmpty() }?.split(':').orEmpty()
        val tail = halves.getOrNull(1)?.takeIf { it.isNotEmpty() }?.split(':').orEmpty()
        val groups = head + List(8 - head.size - tail.size) { "0" } + tail
        val hi = (0 until 4).fold(0uL) { acc, i -> (acc shl 16) or groups[i].toULong(16) }
        val lo = (4 until 8).fold(0uL) { acc, i -> (acc shl 16) or groups[i].toULong(16) }
        return hi to lo
    }

    private fun formatV6(hi: ULong, lo: ULong): String {
        fun groups(value: ULong): String =
            (0 until 4).joinToString(":") { i -> ((value shr (48 - 16 * i)) and 0xFFFFu).toString(16) }
        return "${groups(hi)}:${groups(lo)}"
    }
}
