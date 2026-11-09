package com.picacompose.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.picacompose.data.pica
import com.picacompose.picaapi.model.Banner
import com.picacompose.ui.components.PicaImage
import com.picacompose.ui.components.url
import kotlinx.coroutines.delay

/** 開屏廣告留几秒，跟官方一样到点自己关。 */
private const val SKIP_SECONDS = 5

private const val AD_LINK_TITLE = "廣告"

/**
 * 嗶咔的開屏廣告：拉 `GET banners` 里 `type=ads` 的那条，整屏铺开。
 *
 * 官方是启动时弹一次；这边只放在分类页「支持嗶咔 / 點擊救嗶咔 / 嗶咔小程式」三块磁贴上，
 * 点开看一次。点图走广告链接，右上角「跳過」随时关。
 */
@Composable
fun SplashAdDialog(
    onDismiss: () -> Unit,
    onOpenLink: (url: String, title: String) -> Unit,
) {
    var ad by remember { mutableStateOf<Banner?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { pica.meta.banners() }
            .onSuccess { list ->
                // 先挑广告那几条；接口哪天混了别的类型，也有得显示。
                ad = list.firstOrNull { it.type == "ads" } ?: list.firstOrNull()
            }
            .onFailure { failed = true }
        loading = false
    }

    // 广告露脸了才开始倒数，别图还没加载完就被关掉。
    var left by remember { mutableIntStateOf(SKIP_SECONDS) }
    LaunchedEffect(ad, failed) {
        if (ad == null && !failed) return@LaunchedEffect
        while (left > 0) {
            delay(1_000)
            left--
        }
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val current = ad
            when {
                loading -> CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )

                current == null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "廣告沒拉到",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "稍後再點一次，或先關掉。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "關閉",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.16f))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 22.dp, vertical = 9.dp),
                    )
                }

                else -> {
                    val link = current.link
                    PicaImage(
                        url = current.thumb.url(),
                        contentDescription = current.title.ifBlank { AD_LINK_TITLE },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(enabled = !link.isNullOrBlank()) {
                                link?.let { onOpenLink(it, AD_LINK_TITLE) }
                            },
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(20.dp),
                    ) {
                        Text(
                            text = "廣告",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Black,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.82f))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                        val line = current.shortDescription ?: current.title
                        if (!line.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = line,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "點擊前往",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            Text(
                text = if (left > 0) "跳過 ${left}s" else "跳過",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(14.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}
