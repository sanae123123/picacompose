package com.picacompose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.awt.Desktop
import java.net.URI

/** 桌面版没有内嵌浏览器：把地址亮出来，交给系统浏览器。 */
@Composable
actual fun PlatformWebView(
    url: String,
    controller: WebViewController,
    modifier: Modifier,
) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "桌面版没有内置浏览器",
            style = MiuixTheme.textStyles.body1,
            color = colors.onSurfaceSecondary,
        )
        Text(
            text = url,
            style = MiuixTheme.textStyles.footnote1,
            color = colors.onSurface,
        )
        TextButton(onClick = { openUrl(url) }) {
            Text("用系统浏览器打开")
        }
    }
}

@Composable
actual fun rememberBrowserOpener(): (String) -> Unit = remember { { url -> openUrl(url) } }

private fun openUrl(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
}
