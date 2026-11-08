package com.picacompose.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 简易内置浏览器：顶栏（关闭 / 标题 / 刷新）+ 下面的网页视图。
 *
 * 分类里 `isWeb` 的条目点了就用它打开服务端给的 link；页面内的跳转都留在容器里。
 */
@Composable
fun WebBrowserDialog(
    title: String?,
    url: String,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // 「刷新」= 把网页视图整个换掉重新请求，比在各平台上各接一套 reload 干净。
    var reloadKey by remember(url) { mutableStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        PicaGlyph(PicaIcon.Back, Modifier.size(22.dp), colors.onBackground)
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = title.orEmpty().ifBlank { "网页" },
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = url,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = "刷新",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { reloadKey++ }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()) {
                    // key 一变就重建网页视图，等于刷新。
                    androidx.compose.runtime.key(reloadKey) {
                        PlatformWebView(url = url, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
