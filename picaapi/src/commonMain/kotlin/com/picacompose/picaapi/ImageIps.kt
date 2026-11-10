package com.picacompose.picaapi

/**
 * 图片请求用的 IP 池。
 *
 * 图床（`storage1` / `storage-b` / `s3` / `img.picacomic.com`）都在 Cloudflare 后面，
 * 而不少网络会把它们的系统解析污染成别的公司的地址，连过去只剩超时。官方客户端靠 `/init`
 * 下发的 IP 池绕过去（它的自定义 DNS 连 hostname 都不看，图片也吃这套），这里照着分成几层：
 *
 * 1. [preferred] —— 后台优选测出来的，按握手耗时排；
 * 2. [official] —— `/init` 下发的池子，也就是官方用的那份；
 * 3. 内置种子（IPv4 + IPv6）—— 前两层都用不上时至少不至于一张图都没有。
 *
 * 冷启动顺序是「官方源先上，后台同时测速，测完把优选结果顶到最前面」（见 [activatePreferred]）：
 * 这样首屏不用等测速，测速结果也不会被上次那份可能已经过期的结果挡住。
 */
object ImageIps {

    /**
     * 内置种子：Cloudflare 的 anycast 地址（v4）。
     *
     * 实测这几台对 storage1 / storage-b / s3 / img 都正常出图；它们只是兜底，
     * 平时用的是后台优选出来的结果。
     */
    private val SEEDS_V4 = listOf("104.19.53.76", "172.66.173.71", "104.20.42.9")

    /** 内置种子（v6）：同一套 anycast，设备有 v6 出口时能用上，没有的话探测会直接失败。 */
    private val SEEDS_V6 = listOf(
        "2606:4700::1",
        "2606:4700:3033::ac43:1",
        "2606:4700:10::6816:1",
    )

    /** 后台优选出来的地址，按延迟从低到高。 */
    @Volatile
    var preferred: List<String> = emptyList()
        private set

    /** `/init` 下发的地址池（官方同款）。 */
    @Volatile
    var official: List<String> = emptyList()
        private set

    /** 设置里的「图片 IP 优选」开关：关掉就只用官方源 + 内置种子，不测也不用优选结果。 */
    @Volatile
    var enabled: Boolean = true
        private set

    /** 优选是否已经测过一轮：false 时官方源优先（冷启动），true 时优选结果优先。 */
    @Volatile
    var preferredActive: Boolean = false
        private set

    fun updatePreferred(ips: List<String>) {
        preferred = ips.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.distinct()
    }

    fun updateOfficial(ips: List<String>) {
        official = ips.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.distinct()
    }

    /** 优选测完，让它顶到官方源前面。 */
    fun activatePreferred() {
        preferredActive = true
    }

    /** 冷启动 / 线路切换后回到「官方源优先」，等新一轮优选。 */
    fun preferOfficialFirst() {
        preferredActive = false
    }

    /** 开关拨一下立刻生效，不用重建客户端（DNS 每次 lookup 都现取）。 */
    fun updateEnabled(value: Boolean) {
        enabled = value
        if (!value) preferredActive = false
    }

    /** 当前该用的顺序：关掉优选只剩官方源 + 种子；开着则冷启动官方源优先、测完优选优先。 */
    fun ordered(): List<String> = when {
        !enabled -> (official + SEEDS_V4 + SEEDS_V6).distinct()
        preferredActive -> (preferred + official + SEEDS_V4 + SEEDS_V6).distinct()
        else -> (official + preferred + SEEDS_V4 + SEEDS_V6).distinct()
    }

    /** 后台测速的候选：几层全都要测，才能知道哪台最快（v6 没出口的自然测不过）。 */
    fun candidates(): List<String> = (preferred + official + SEEDS_V4 + SEEDS_V6).distinct()
}

/**
 * 测一遍 [candidates]，按「TLS 握手 + 首个响应」的耗时从快到慢返回前 [limit] 个。
 *
 * 做法是拿每台地址当 DNS 解析结果去 `HEAD` 一次接口根路径：能过 TLS 且不是 Cloudflare 的
 * 「禁止直连 IP」（403）就算可用；IPv6 地址在没有 v6 出口的设备上会直接失败，不影响 v4。
 *
 * 耗时在 [thresholdMs] 以内的算「快」，优先从里面取；不够 [limit] 个时用剩下的按延迟补满，
 * 免得某条网络整体偏慢时把池子清空。全部失败返回空列表，调用方保留原来的池子。
 * 平台没有能单独指定 IP 的 HTTP 栈时也返回空列表。
 */
expect suspend fun probeImageIps(
    candidates: List<String>,
    limit: Int = 10,
    thresholdMs: Long = 500,
    onProgress: ((tested: Int, total: Int, fastestMs: Long?) -> Unit)? = null,
): List<String>
