package com.picacompose.picaapi

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

private const val PICACOMIC_SUFFIX = "picacomic.com"

/**
 * 图片请求专用的 DNS：picacomic 的域名按 [ImageIps] 的顺序钉地址，系统解析出来的排在后面兜底。
 *
 * 顺序很关键 —— 被污染的系统解析结果是「能解析、连不上」，放前面就是每张图先等一轮超时。
 * 池子会随后台优选结果变化，所以每次 lookup 都现取，不做整份缓存。
 */
private class ImageDns : Dns {

    /** 交给 OkHttp 的地址对象：同一台地址会连着请求，转一次存一次。 */
    private val cache = java.util.concurrent.ConcurrentHashMap<String, InetAddress>()

    private fun address(ip: String): InetAddress? = cache[ip]?.let { it }
        ?: runCatching { InetAddress.getByName(ip) }.getOrNull()?.also { cache[ip] = it }

    override fun lookup(hostname: String): List<InetAddress> {
        if (!hostname.endsWith(PICACOMIC_SUFFIX, ignoreCase = true)) return Dns.SYSTEM.lookup(hostname)
        val pooled = ImageIps.ordered().mapNotNull(::address)
        val system = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrDefault(emptyList())
        val merged = (pooled + system).distinctBy { it.hostAddress }
        if (merged.isEmpty()) throw UnknownHostException("Unable to resolve host \"$hostname\"")
        return merged
    }
}

actual fun imageEngine(): HttpClientEngine? = OkHttp.create {
    config {
        dns(ImageDns())
    }
}
