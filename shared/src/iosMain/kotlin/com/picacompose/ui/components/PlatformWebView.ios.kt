package com.picacompose.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.UIKit.UIApplication
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

@Composable
actual fun PlatformWebView(
    url: String,
    controller: WebViewController,
    modifier: Modifier,
) {
    var webView by remember { mutableStateOf<WKWebView?>(null) }

    UIKitView(
        factory = {
            WKWebView(
                frame = CGRectZero.readValue(),
                configuration = WKWebViewConfiguration(),
            ).also { webView = it }
        },
        modifier = modifier,
        update = { view ->
            // 同一个地址不重载，只有地址变了才重新请求。
            if (view.URL?.absoluteString != url) {
                NSURL.URLWithString(url)?.let { target ->
                    view.loadRequest(NSURLRequest.requestWithURL(target))
                }
            }
            controller.canGoBack = view.canGoBack
        },
    )

    LaunchedEffect(controller.reloadTick) {
        if (controller.reloadTick > 0) webView?.reload()
    }
    LaunchedEffect(controller.backTick) {
        val view = webView ?: return@LaunchedEffect
        if (controller.backTick > 0 && view.canGoBack) view.goBack()
    }
}

@Composable
actual fun rememberBrowserOpener(): (String) -> Unit = remember {
    { url ->
        NSURL.URLWithString(url)?.let { target ->
            UIApplication.sharedApplication.openURL(target)
        }
    }
}
