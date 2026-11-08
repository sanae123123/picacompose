package com.picacompose.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun PlatformWebView(url: String, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // 站内跳转都留在容器里，别把外部浏览器叫出来。
                webViewClient = WebViewClient()
            }
        },
        update = { view ->
            // update 每次重组都会跑，同一个地址不重载。
            if (view.url != url) view.loadUrl(url)
        },
    )
}
