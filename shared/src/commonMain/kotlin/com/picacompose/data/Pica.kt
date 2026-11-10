package com.picacompose.data

import com.picacompose.picaapi.PicaClient
import com.picacompose.picaapi.pinnedEngine

/**
 * 当前使用的客户端。分流选中线路后会整个换掉 —— DNS 是建引擎那一刻定下的，改不了热生效，
 * 官方客户端也是靠重建 OkHttpClient（`MyApplication.bw()`）来切换的。
 */
var pica: PicaClient = PicaClient()
    private set

/** 请求在传输层失败时的回调，由 App 装上；换线路的逻辑放在 [LineRouter] 里。 */
var picaNetworkFailureHandler: (() -> Unit)? = null

/**
 * 按线路重建客户端，把登录态一起带过去。
 *
 * 线路 1 不钉 DNS；线路 2/3 用下发 IP 池，平台不支持时 `pinnedEngine` 返回 null，退化成系统解析。
 */
suspend fun applyPicaRoute(line: PicaLine, ips: List<String>) {
    val old = pica
    val engine = if (line.pinnedDns) pinnedEngine(ips) else null
    pica = PicaClient(
        config = old.config.copy(
            appChannel = line.channel,
            authorization = old.token.value,
            timeOffsetSeconds = old.serverTimeOffsetSeconds,
        ),
        engine = engine,
        onTransportFailure = { picaNetworkFailureHandler?.invoke() },
    )
    old.close()
}
