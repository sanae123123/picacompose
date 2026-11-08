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
                override fun lookup(hostname: String): List<InetAddress> {
                    val resolved = ips.mapNotNull { ip ->
                        runCatching { InetAddress.getByName(ip) }.getOrNull()
                    }
                    // 官方 b.b#lookup 在下发为空时返回空列表，会直接连不上；
                    // 这里多一层兜底，池子解析全失败就退回系统 DNS。
                    return resolved.ifEmpty { Dns.SYSTEM.lookup(hostname) }
                }
            })
        }
    }
}
