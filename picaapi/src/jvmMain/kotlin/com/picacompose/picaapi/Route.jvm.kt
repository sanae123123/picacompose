package com.picacompose.picaapi

import io.ktor.client.engine.HttpClientEngine

/** 桌面端用 CIO 引擎，没有 DNS 钩子；线路 2/3 会退化成系统解析。 */
actual suspend fun pinnedEngine(ips: List<String>): HttpClientEngine? = null

/** 桌面端用 CIO 引擎，没有 DNS 钩子：DoH 开关在桌面端不生效，退回系统解析。 */
actual fun dohEngine(pinnedIps: List<String>): HttpClientEngine? = null

/** 桌面端用 CIO 引擎，没有 DNS 钩子：图片还是按系统解析。 */
actual fun imageEngine(): HttpClientEngine? = null

/** 这个平台没有能按 IP 指定目标地址的 HTTP 栈，优选不生效，返回空列表沿用原池子。 */
actual suspend fun probeImageIps(
    candidates: List<String>,
    limit: Int,
    thresholdMs: Long,
    onProgress: ((tested: Int, total: Int, fastestMs: Long?) -> Unit)?,
): List<String> = emptyList()
