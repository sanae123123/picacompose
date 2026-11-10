package com.picacompose.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.picacompose.picaapi.CloudflareIps
import com.picacompose.picaapi.ImageIps
import com.picacompose.picaapi.probeImageIps
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val preferences = LinePreferences()
    // 冷启动：先用 `/init` 下发的官方源顶上，上次的优选结果只当第二层。
    ImageIps.updatePreferred(store.savedImageIps)
    ImageIps.updateOfficial(store.savedIps)
    ImageIps.updateEnabled(preferences.imageIps)
    ImageIps.preferOfficialFirst()
    // 首屏先用官方源出图，扫描往后让一让：几百台一起握手会跟首屏的图抢连接。
    delay(START_DELAY_MS)
    while (true) {
        if (!preferences.imageIps) {
            // 开关关着：不测、也不用优选结果，只用官方下发地址（设置页随时能开回来）。
            ImageIps.updateEnabled(false)
            ImageIps.preferOfficialFirst()
            ImageIpsStatus.off()
            delay(IDLE_MS)
            continue
        }
        ImageIps.updateEnabled(true)
        val candidates = CloudflareIps.sample()
        ImageIpsStatus.start(candidates.size)
        // 边测边用：先测出够快的几台就立刻顶上，不用等 200 多台全扫完。
        val best = coroutineScope {
            val hits = Channel<Pair<String, Long>>(Channel.UNLIMITED)
            val applier = launch {
                val usable = ArrayList<Pair<String, Long>>()
                for (hit in hits) {
                    usable.add(hit)
                    val top = usable.sortedBy { it.second }
                        .take(POOL_SIZE)
                        .map { it.first }
                    ImageIps.updatePreferred(top)
                    ImageIps.activatePreferred()
                    ImageIpsStatus.progress(
                        tested = ImageIpsStatus.tested,
                        fastestMs = usable.minOf { it.second },
                        live = top,
                    )
                }
            }
            val result = runCatching {
                probeImageIps(
                    candidates = candidates,
                    limit = POOL_SIZE,
                    thresholdMs = FAST_MS,
                    onProgress = { tested, _, fastest ->
                        ImageIpsStatus.progress(tested = tested, fastestMs = fastest)
                    },
                    onHit = { ip, ms -> hits.trySend(ip to ms) },
                )
            }.getOrDefault(emptyList())
            hits.close()
            applier.join()
            result
        }
        if (best.isNotEmpty()) {
            // 测完就把官方源让到后面：优选结果是现测的，比下发的那几台更贴近当前网络。
            ImageIps.updatePreferred(best)
            ImageIps.activatePreferred()
            store.saveImageIps(best)
        }
        ImageIpsStatus.finish(best)
        delay(if (best.isEmpty()) RETRY_MS else INTERVAL_MS)
    }
}

/** 池子里留几台：够并发用，又不至于每张图都换一条新连接。 */
private const val POOL_SIZE = 10

/** 多快算「快」：500ms 以内的握手速度，一屏图基本不会等。 */
private const val FAST_MS = 500L

/** 开关关着时每隔一会儿看一眼，拨开了就能接着测。 */
private const val IDLE_MS = 30 * 1000L

/** 进主界面之后才开扫，让首屏那几张图先跑完。 */
private const val START_DELAY_MS = 3_000L

/** 全段扫描比只测几台重，半小时一次足够跟上线路抖动。 */
private const val INTERVAL_MS = 30 * 60 * 1000L

/** 全都没测出来时别等 15 分钟，1 分钟后换个时间点再试。 */
private const val RETRY_MS = 60 * 1000L
