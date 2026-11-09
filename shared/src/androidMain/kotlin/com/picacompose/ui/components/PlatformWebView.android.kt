package com.picacompose.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
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
                // http/https 留在容器里；别的 scheme（intent://、market://、tg:// 之类）
                // 交给本机上装了对应 App 的去处理，没人接就安静地吞掉，别让网页跳错误页。
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean = view.openExternalIfNeeded(request.url.toString())

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

/**
 * 网页里点到非 http(s) 的链接时，看看本机有没有 App 能接。
 *
 * 返回值就是 `shouldOverrideUrlLoading` 的返回值：true 表示这次跳转我们处理掉了，
 * 网页不用自己去加载（否则 WebView 会显示个「网页无法打开」）。
 *
 * - http / https：false，留在内置浏览器里。
 * - `intent://`：按 Intent URI 解析，有对应的就打开；没有则退回它自带的
 *   `browser_fallback_url`（如果有），再不行就什么都不做。
 * - 其它 scheme：试着用系统 VIEW 打开，没有 App 接就安静地忽略 —— 不弹错误、不崩。
 */
private fun WebView.openExternalIfNeeded(url: String): Boolean {
    val scheme = runCatching { Uri.parse(url).scheme?.lowercase() }.getOrNull()
    if (scheme == null || scheme == "http" || scheme == "https") return false

    val context = context
    if (scheme == "intent") {
        val intent = runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME) }
            .getOrNull() ?: return true
        val fallback = intent.getStringExtra("browser_fallback_url")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opened = runCatching { context.startActivity(intent) }.isSuccess
        if (!opened && !fallback.isNullOrBlank()) loadUrl(fallback)
        return true
    }

    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.packageManager.resolveActivity(intent, 0)?.let {
        runCatching { context.startActivity(intent) }
    }
    return true
}
