package com.picacompose.picaapi

/**
 * 图片请求用的 IP 池。
 *
 * 图床（`storage1` / `storage-b` / `s3` / `img.picacomic.com`）都在 Cloudflare 后面，
 * 而不少网络会把它们的系统解析污染成别的公司的地址，连过去只剩超时。官方客户端靠 `/init`
 * 下发的 IP 池绕过去（它的自定义 DNS 连 hostname 都不看，图片也吃这套），这里照着分成三层，
 * 前面的先用：
 *
 * 1. [preferred] —— 后台优选测出来的，按握手耗时排，最快的那几台；
 * 2. [official] —— `/init` 下发的池子，也就是官方用的那份；
 * 3. [SEEDS] —— 内置的 Cloudflare 地址，前两层都空的时候至少不至于一张图都没有。
 */
object ImageIps {

    /**
     * 内置种子：Cloudflare 的 anycast 地址。
     *
     * 实测这几台对 storage1 / storage-b / s3 / img 都正常出图；它们只是兜底，
     * 平时用的是后台优选出来的结果。
     */
    private val SEEDS = listOf("104.19.53.76", "172.66.173.71", "104.20.42.9")

    /** 后台优选出来的地址，按延迟从低到高。 */
    @Volatile
    var preferred: List<String> = emptyList()
        private set

    /** `/init` 下发的地址池（官方同款）。 */
    @Volatile
    var official: List<String> = emptyList()
        private set

    fun updatePreferred(ips: List<String>) {
        preferred = ips.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.distinct()
    }

    fun updateOfficial(ips: List<String>) {
        official = ips.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.distinct()
    }

    /** 当前该用的顺序：优选 → 官方下发 → 内置种子。 */
    fun ordered(): List<String> = (preferred + official + SEEDS).distinct()
}

/**
 * 测一遍 [candidates] 能不能用，按「TLS 握手 + 首个响应」的耗时从快到慢返回前 [limit] 个。
 *
 * 做法是拿每台地址当 DNS 解析结果去 `HEAD` 一次接口根路径：能过 TLS 且不是 Cloudflare 的
 * 「禁止直连 IP」（403）就算可用。全部失败时返回空列表，调用方保留原来的池子。
 *
 * 平台没有能单独指定 IP 的 HTTP 栈时返回空列表（桌面 / iOS）。
 */
expect suspend fun probeImageIps(candidates: List<String>, limit: Int = 3): List<String>
