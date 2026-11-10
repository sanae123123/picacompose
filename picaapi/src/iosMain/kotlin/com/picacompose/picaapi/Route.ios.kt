package com.picacompose.picaapi

import io.ktor.client.engine.HttpClientEngine

/** Darwin 引擎不暴露 DNS 覆盖；线路 2/3 会退化成系统解析。 */
actual suspend fun pinnedEngine(ips: List<String>): HttpClientEngine? = null

/** Darwin 引擎同样没有 DNS 钩子：DoH 开关在 iOS 上不生效，退回系统解析。 */
actual fun dohEngine(pinnedIps: List<String>): HttpClientEngine? = null

/** Darwin 引擎没有 DNS 钩子：图片还是按系统解析。 */
actual fun imageEngine(): HttpClientEngine? = null
