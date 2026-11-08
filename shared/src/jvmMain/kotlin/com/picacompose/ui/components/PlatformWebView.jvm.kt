package com.picacompose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.awt.Desktop
import java.net.URI

/** 桌面版没有内嵌浏览器：把地址亮出来，交给系统浏览器。 */
@Composable
actual fun PlatformWebView(url: String, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "桌面版没有内置浏览器",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Text(
            text = url,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurface,
        )
        TextButton(
            onClick = { runCatching { Desktop.getDesktop().browse(URI(url)) } },
        ) {
            Text("用系统浏览器打开")
        }
    }
}
