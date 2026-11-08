package com.picacompose.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 简易内置浏览器整页：顶栏（返回 / 标题 / 地址 / 汉堡菜单）+ 下面的网页视图。
 *
 * 分类里 `isWeb` 的条目点了就用它打开服务端给的 link，页面内的跳转都留在容器里。
 * 它和别的整页一样走容器变形（从磁贴那一块长成整屏），所以这里只管内容、不管底色和系统栏。
 */
@Composable
fun WebPageContent(
    title: String?,
    url: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** 变形动画放完再挂网页视图：Android 的 WebView 是独立渲染的平台视图，不跟着容器变形走，
     *  挂早了会直接以整屏尺寸弹出来，看着就不是从磁贴长出来的。 */
    showWebView: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val controller = rememberWebViewController()
    val openInBrowser = rememberBrowserOpener()
    var menuOpen by remember { mutableStateOf(false) }
    var pageUrl by remember(url) { mutableStateOf(url) }
    var pageTitle by remember(url) { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onBack),
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
                    text = pageUrl,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                    PicaGlyph(PicaIcon.Menu, Modifier.size(22.dp), colors.onBackground)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("刷新") },
                        onClick = {
                            menuOpen = false
                            controller.reload()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("後退") },
                        enabled = controller.canGoBack,
                        onClick = {
                            menuOpen = false
                            controller.goBack()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("返回") },
                        onClick = {
                            menuOpen = false
                            onBack()
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("用默认浏览器打开") },
                        onClick = {
                            menuOpen = false
                            openInBrowser(pageUrl.ifBlank { url })
                        },
                    )
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (showWebView) {
                // key 一变就重建网页视图（比如地址变了），等于重新加载。
                key(url) {
                    PlatformWebView(
                        url = url,
                        controller = controller,
                        modifier = Modifier
                            .fillMaxSize()
                            // 网页视图盖在上面时，别让手势漏到下面那一层。
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent(PointerEventPass.Main).changes.forEach { it.consume() }
                                    }
                                }
                            },
                    )
                }
            }
        }
    }
}
