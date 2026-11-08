package com.picacompose.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun PlatformWebView(
    url: String,
    controller: WebViewController,
    modifier: Modifier,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // 站内跳转都留在容器里，别把外部浏览器叫出来。
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, pageUrl: String?) {
                        super.onPageFinished(view, pageUrl)
                        controller.canGoBack = view.canGoBack()
                    }
                }
            }.also { webView = it }
        },
        update = { view ->
            // update 每次重组都会跑，同一个地址不重载。
            if (view.url != url) view.loadUrl(url)
        },
    )

    LaunchedEffect(controller.reloadTick) {
        if (controller.reloadTick > 0) webView?.reload()
    }
    LaunchedEffect(controller.backTick) {
        val view = webView ?: return@LaunchedEffect
        if (controller.backTick > 0 && view.canGoBack()) view.goBack()
    }
}

@Composable
actual fun rememberBrowserOpener(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) { { url -> context.openInBrowser(url) } }
}

private fun Context.openInBrowser(url: String) {
    runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
