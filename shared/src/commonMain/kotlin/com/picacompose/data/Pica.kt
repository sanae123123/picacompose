package com.picacompose.data

import com.picacompose.picaapi.PicaClient
import com.picacompose.picaapi.dohEngine
import com.picacompose.picaapi.pinnedEngine
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 当前使用的客户端。分流选中线路后会整个换掉 —— DNS 是建引擎那一刻定下的，改不了热生效，
 * 官方客户端也是靠重建 OkHttpClient（`MyApplication.bw()`）来切换的。
 */
var pica: PicaClient = PicaClient()
    private set

/** 请求在传输层失败时的回调，由 App 装上；换线路的逻辑放在 [LineRouter] 里。 */
var picaNetworkFailureHandler: (() -> Unit)? = null

/** 退役客户端晚点再关的地方：立刻关会把还在飞的请求一起掐掉，用户就看到报错了。 */
private val retireScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * 按线路重建客户端，把登录态一起带过去。
 *
 * 线路 1 不钉 DNS；线路 2/3 用下发 IP 池，平台不支持时 `pinnedEngine` 返回 null，退化成系统解析。
 * 设置里打开 DoH 时优先用 DoH 引擎：钉住的线只钉接口域名，其它域名也一并走 DoH。
 */
suspend fun applyPicaRoute(line: PicaLine, ips: List<String>) {
    val old = pica
    val engine = routeEngine(line, ips)
    pica = PicaClient(
        config = old.config.copy(
            appChannel = line.channel,
            authorization = old.token.value,
            timeOffsetSeconds = old.serverTimeOffsetSeconds,
            // /init 下发的图床换线时要带过去，不然新客户端会退回只用 fileServer。
            imageServer = old.currentImageServer ?: RouteStore().savedImageServer,
        ),
        engine = engine,
        onTransportFailure = { picaNetworkFailureHandler?.invoke() },
    )
    // 不立刻 close：刚换上来的线路一般几秒内就稳了，等旧客户端把手上的请求跑完。
    retireScope.launch {
        delay(8_000)
        old.close()
    }
}

/** DoH 开着就拿 DoH 引擎（能返回 null 的平台会退回原来那套）；关着还是按线路钉 IP。 */
private suspend fun routeEngine(line: PicaLine, ips: List<String>): HttpClientEngine? {
    if (LinePreferences().doh) {
        val doh = dohEngine(if (line.pinnedDns) ips else emptyList())
        if (doh != null) return doh
    }
    return if (line.pinnedDns) pinnedEngine(ips) else null
}
