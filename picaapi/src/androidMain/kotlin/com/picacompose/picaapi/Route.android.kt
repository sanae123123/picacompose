package com.picacompose.picaapi

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Dns
import java.net.InetAddress

actual suspend fun pinnedEngine(ips: List<String>): HttpClientEngine? {
    if (ips.isEmpty()) return null
    return OkHttp.create {
        config {
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
                    // 官方 b.b#lookup 在下发为空时返回空列表，会直接连不上；
                    // 这里多一层兜底，池子解析全失败就退回系统 DNS。
                    return resolved.ifEmpty { Dns.SYSTEM.lookup(hostname) }
                }
            })
        }
    }
}
