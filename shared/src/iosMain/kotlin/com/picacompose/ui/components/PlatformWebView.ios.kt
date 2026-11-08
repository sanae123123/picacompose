package com.picacompose.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

@Composable
actual fun PlatformWebView(url: String, modifier: Modifier) {
    UIKitView(
        factory = {
            WKWebView(
                frame = CGRectZero.readValue(),
                configuration = WKWebViewConfiguration(),
            )
        },
        modifier = modifier,
        update = { webView ->
            // 同一个地址不重载，只有地址变了才重新请求。
            if (webView.URL?.absoluteString != url) {
                NSURL.URLWithString(url)?.let { target ->
                    webView.loadRequest(NSURLRequest.requestWithURL(target))
                }
            }
        },
    )
}
