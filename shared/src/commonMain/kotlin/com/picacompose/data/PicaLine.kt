package com.picacompose.data

/**
 * 三条线路，和官方 App 启动页的三个按钮一一对应。
 *
 * 域名永远是 `picaapi.picacomic.com`，差别只在「要不要把 `/init` 下发的 IP 池钉进 DNS」，
 * 以及上报的 `app-channel`。线路 2 和 3 走的是同一套 IP，只是通道号不同。
 */
enum class PicaLine(
    val channel: Int,
    val title: String,
    val detail: String,
    val pinnedDns: Boolean,
) {
    System(1, "线路 1", "系统解析 · 域名没被污染时最快", false),
    PinnedFast(2, "线路 2", "下发 IP · 选完立即生效", true),
    PinnedAlt(3, "线路 3", "下发 IP · 备用通道", true),
}
