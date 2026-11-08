package com.picacompose.data

/**
 * 记住上次选的线路和下发到的 IP 池。
 *
 * 只用来做「上次用线路 2」这类提示和离线兜底；每次冷启动仍会重测一遍，网络变了随时能换。
 */
class RouteStore(private val kv: KeyValueStore = platformKeyValueStore()) {

    val savedLine: PicaLine?
        get() = kv.get(KEY_LINE)?.toIntOrNull()?.let { channel ->
            PicaLine.entries.firstOrNull { it.channel == channel }
        }

    val savedIps: List<String>
        get() = kv.get(KEY_IPS)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    val savedLatencyMs: Long?
        get() = kv.get(KEY_LATENCY)?.toLongOrNull()

    fun save(line: PicaLine?, ips: List<String>, latencyMs: Long?) {
        kv.put(KEY_LINE, line?.channel?.toString())
        kv.put(KEY_IPS, ips.takeIf { it.isNotEmpty() }?.joinToString(","))
        kv.put(KEY_LATENCY, latencyMs?.toString())
    }

    private companion object {
        const val KEY_LINE = "route.line"
        const val KEY_IPS = "route.dnsIps"
        const val KEY_LATENCY = "route.latencyMs"
    }
}
