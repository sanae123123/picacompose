package com.picacompose.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.picacompose.picaapi.ImageIps
import com.picacompose.picaapi.probeImageIps
import kotlinx.coroutines.delay

/**
 * 图片服务器（`/init` 下发的那个）。
 *
 * 接口给的 `thumb.fileServer` 大多是官方图床 `storage1.picacomic.com`，而这个域名在很多网络上
 * 被解析到了错的地址（本机就解析成 Dropbox 的 108.160.172.200，连过去必然超时），图就永远出不来。
 * 官方客户端拼地址时优先用 `/init` 下发的 imageServer，那是能通的镜像图床，所以官方出图、我们不出。
 * 这里存同一份，图片拼地址时优先用它。
 *
 * 用快照状态存：`/init` 回来晚于第一批图时，晚到的地址会把已经拼好的图片地址换掉，重新拉一次。
 */
internal object ImageHosts {

    var server: String? by mutableStateOf(null)
        private set

    fun update(value: String?) {
        val next = value?.trim()?.takeIf { it.isNotEmpty() }
        if (next != server) server = next
    }
}

/**
 * 拉一次 `/init`，把图床地址记下来（内存 + 本机）。
 *
 * 拿不到就什么都不做：图床地址只是「优先用它」，没有也能按 `fileServer` 拼。
 */
internal suspend fun refreshImageHost() {
    // `/init` 顺带把官方 IP 池记一下：优选测出来之前先用它顶着。
    ImageIps.updateOfficial(RouteStore().savedIps)
    val data = runCatching { pica.meta.init() }.getOrNull() ?: return
    val server = data.imageServer?.takeIf { it.isNotBlank() } ?: return
    RouteStore().saveImageServer(server)
    ImageHosts.update(server)
}

/**
 * 后台跑图片 IP 的优选：开机测一轮，之后每 [INTERVAL_MS] 重测。
 *
 * 只做一件事 —— 按握手耗时给池子排序，挑最快的那几台给图片 DNS 用。全都没测出来就保留
 * 原来的池子（上次的结果 / 内置种子），不会把能用的地址清掉。
 */
internal suspend fun keepImageIpsFresh() {
    val store = RouteStore()
    ImageIps.updatePreferred(store.savedImageIps)
    ImageIps.updateOfficial(store.savedIps)
    while (true) {
        val best = runCatching { probeImageIps(ImageIps.ordered()) }.getOrDefault(emptyList())
        if (best.isNotEmpty()) {
            ImageIps.updatePreferred(best)
            store.saveImageIps(best)
        }
        delay(if (best.isEmpty()) RETRY_MS else INTERVAL_MS)
    }
}

/** 优选重测的间隔：15 分钟足够跟上线路抖动，又不会一直在后台耗电。 */
private const val INTERVAL_MS = 15 * 60 * 1000L

/** 全都没测出来时别等 15 分钟，1 分钟后换个时间点再试。 */
private const val RETRY_MS = 60 * 1000L
