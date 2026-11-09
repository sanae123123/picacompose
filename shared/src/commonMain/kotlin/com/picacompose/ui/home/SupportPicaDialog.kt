package com.picacompose.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.PlatformWebView
import com.picacompose.ui.components.rememberWebViewController

/**
 * 原版 `KEY_AD_URL_BASE` 的默认值，`/android/font` 就是「支持嗶咔」那个页面。
 *
 * 原版走 http，这边用 https：Android 9 起默认禁明文，而服务端两套都开着。
 */
private const val SUPPORT_BASE_URL = "https://pica-juicy.picacomic.com/android/font"

/**
 * 「支持嗶咔」内嵌页：照原版 PopupWebview 的做法，整屏盖一层网页，右上角「關閉」。
 *
 * 原版就是把启动弹窗广告那个组件拿来复用，URL 写死在客户端、不带 token；
 * 主题色是黑的时加 `?color=black`，页面自己换深色配色。
 */
@Composable
fun SupportPicaDialog(onDismiss: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val url = if (dark) "$SUPPORT_BASE_URL?color=black" else SUPPORT_BASE_URL
    val colors = MaterialTheme.colorScheme
    val controller = rememberWebViewController()

    Dialog(
        onDismissRequest = onDismiss,
        // 网页铺到系统栏下面，栏位留白自己在里面补。
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "支持嗶咔",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                        PicaGlyph(
                            icon = PicaIcon.Close,
                            modifier = Modifier.size(20.dp),
                            tint = colors.onSurfaceVariant,
                        )
                    }
                }
                PlatformWebView(
                    url = url,
                    controller = controller,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars),
                )
            }
        }
    }
}
