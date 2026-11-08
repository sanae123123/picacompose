package com.picacompose.picaapi

import io.ktor.client.engine.HttpClientEngine

/** Darwin 引擎不暴露 DNS 覆盖；线路 2/3 会退化成系统解析。 */
actual suspend fun pinnedEngine(ips: List<String>): HttpClientEngine? = null
