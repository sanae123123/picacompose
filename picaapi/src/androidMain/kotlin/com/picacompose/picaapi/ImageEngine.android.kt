package com.picacompose.picaapi

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * `picacomic.com` 这一组图床后面的 Cloudflare anycast 地址。
 *
 * 从 DoH（doh.pub）拿到的真实解析结果，实测这三台对 storage1 / storage-b / s3 都能正常握手出图；
 * TLS 证书按域名校验，所以给错地址也串不到别的站，连不上就换下一台。
 */
private val PICACOMIC_IPS = listOf("104.19.53.76", "172.66.173.71", "104.20.42.9")

private const val PICACOMIC_SUFFIX = "picacomic.com"

/**
 * 图片请求专用的 DNS：picacomic 的域名先试上面那组地址，系统解析出来的排在后面兜底。
 *
 * 顺序很关键 —— 被污染的系统解析结果是「能解析、连不上」，放前面就是每张图先等一轮超时。
 */
private class ImageDns : Dns {

    private val known: List<InetAddress> = PICACOMIC_IPS.mapNotNull { ip ->
        runCatching { InetAddress.getByName(ip) }.getOrNull()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        if (!hostname.endsWith(PICACOMIC_SUFFIX, ignoreCase = true)) return Dns.SYSTEM.lookup(hostname)
        val system = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrDefault(emptyList())
        val merged = (known + system).distinctBy { it.hostAddress }
        if (merged.isEmpty()) throw UnknownHostException("Unable to resolve host \"$hostname\"")
        return merged
    }
}

actual fun imageEngine(): HttpClientEngine? = OkHttp.create {
    config {
        dns(ImageDns())
    }
}
