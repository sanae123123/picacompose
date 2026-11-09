package com.picacompose.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
    // 网页视图是平台视图，挂上那一刻只能整屏硬切；淡进来才接得上容器变形。
    val webAlpha = remember { Animatable(if (showWebView) 1f else 0f) }
    LaunchedEffect(showWebView) {
        if (showWebView) webAlpha.animateTo(1f, tween(durationMillis = 220)) else webAlpha.snapTo(0f)
    }

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
        // 顶栏下面那条加载进度：高度一直占着，出现 / 消失时网页不会上下跳。
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            if (controller.progress > 0.01f && controller.progress < 1f) {
                LinearProgressIndicator(
                    progress = { controller.progress },
                    modifier = Modifier.fillMaxSize(),
                    color = colors.primary,
                    trackColor = colors.primary.copy(alpha = 0.12f),
                )
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer { alpha = webAlpha.value },
        ) {
            if (showWebView) {
                // key 一变就重建网页视图（比如地址变了），等于重新加载。
                key(url) {
                    // 别在网页视图上再套手势拦截：平台视图要自己吃触摸事件，
                    // 底下拦一层会把滚动一起吃掉（之前就是滚不动）。
                    PlatformWebView(
                        url = url,
                        controller = controller,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
