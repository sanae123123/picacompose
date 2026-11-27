package com.picacompose.picaapi

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Dns
import java.net.InetAddress

/**
 * 不限并发的 Dispatcher：开多少路由调用方自己决定（图片一屏多少张、测速 12 路、
 * 阅读器竞速 5 路都有自己的上限）。OkHttp 默认的「同域名 5 路 / 全局 64 路」是它
 * 不知道调用方在干什么，只会在真需要的时候把请求白白排住。
 */
internal fun unlimitedDispatcher() = okhttp3.Dispatcher().apply {
    maxRequests = Int.MAX_VALUE
    maxRequestsPerHost = Int.MAX_VALUE
}

actual fun systemEngine(): HttpClientEngine? = OkHttp.create {
    config {
        dispatcher(unlimitedDispatcher())
        dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val resolved = Dns.SYSTEM.lookup(hostname)
                NetworkLog.record("接口", resolved.firstOrNull()?.hostAddress)
                return resolved
            }
        })
    }
}

actual suspend fun pinnedEngine(ips: List<String>): HttpClientEngine? {
    if (ips.isEmpty()) return null
    return OkHttp.create {
        config {
            dispatcher(unlimitedDispatcher())
            dns(object : Dns {
                private fun address(ip: String): InetAddress? =
                    runCatching { InetAddress.getByName(ip) }.getOrNull()

                override fun lookup(hostname: String): List<InetAddress> {
                    // 接口和图片吃同一份顺序：优选池在前、`/init` 下发的钉 IP 在后。
                    // 连接出问题时 [ImageIps.rotate] 把池子整体挪一位，下一台顶上，不用换线路。
                    val pool = ImageIps.ordered()
                    val resolved = (pool + ips).distinct()
                        .mapNotNull(::address)
                        .distinctBy { it.hostAddress }
                    NetworkLog.record("接口", resolved.firstOrNull()?.hostAddress)
                    // 官方 b.b#lookup 在下发为空时返回空列表，会直接连不上；
                    // 这里多一层兜底，池子解析全失败就退回系统 DNS。
                    return resolved.ifEmpty { Dns.SYSTEM.lookup(hostname) }
                }
            })
        }
    }
}
