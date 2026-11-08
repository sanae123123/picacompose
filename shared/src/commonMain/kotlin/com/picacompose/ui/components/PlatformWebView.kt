package com.picacompose.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * 网页视图的外部控制器：顶栏的「刷新」「後退」按它下发，页面能不能后退由平台回填。
 *
 * 用请求计数而不是直接调方法，是因为真正的 WebView 活在平台那一侧，
 * 这里只能通过状态变化通知它。
 */
@Stable
class WebViewController {
    /** 当前页面能不能后退（第一页就是 false）。 */
    var canGoBack by mutableStateOf(false)
        internal set

    internal var reloadTick by mutableIntStateOf(0)
        private set
    internal var backTick by mutableIntStateOf(0)
        private set

    /** 重新请求当前地址。 */
    fun reload() {
        reloadTick++
    }

    /** 退回网页里的上一页。 */
    fun goBack() {
        backTick++
    }
}

@Composable
fun rememberWebViewController(): WebViewController = remember { WebViewController() }

/**
 * 平台各自的网页视图：Android 是 WebView，iOS 是 WKWebView，桌面没有内嵌浏览器。
 *
 * 只做最低限度的事——把 [url] 打开、页面内链接不跳出容器，并把「能不能后退」报给 [controller]。
 */
@Composable
expect fun PlatformWebView(
    url: String,
    controller: WebViewController,
    modifier: Modifier = Modifier,
)

/**
 * 用系统默认浏览器打开链接。
 *
 * 拿不到就返回 false，调用方自己决定要不要提示。
 */
@Composable
expect fun rememberBrowserOpener(): (String) -> Unit
