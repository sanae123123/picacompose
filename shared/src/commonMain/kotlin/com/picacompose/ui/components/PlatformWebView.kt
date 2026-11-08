package com.picacompose.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 平台各自的网页视图：Android 是 WebView，iOS 是 WKWebView，桌面没有内嵌浏览器。
 *
 * 只做最低限度的事——把 [url] 打开、页面内链接不跳出容器。
 */
@Composable
expect fun PlatformWebView(url: String, modifier: Modifier = Modifier)
