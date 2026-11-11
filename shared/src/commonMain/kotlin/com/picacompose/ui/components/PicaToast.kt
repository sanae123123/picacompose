package com.picacompose.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.picacompose.ui.theme.SkinTokens
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 全局短提示。
 *
 * 自己画一个浮在底部的气泡，不用系统 Toast（iOS/Desktop 上也没有），
 * 也不用 Snackbar（需要 Scaffold，这里很多页面在外面）。
 */
object PicaToast {

    var message by mutableStateOf<String?>(null)
        private set

    fun show(text: String) {
        message = text
    }

    internal fun dismiss(text: String?) {
        if (message == text) message = null
    }
}

/** 挂在 App 根部的气泡宿主；同一时间只显示一条，后到的会顶掉前一条。 */
@Composable
fun PicaToastHost(modifier: Modifier = Modifier) {
    val text = PicaToast.message
    // 退出动画期间 text 已经是 null，留住上一句，别闪成空气泡。
    var lastShown by mutableStateOf("")
    LaunchedEffect(text) {
        if (text == null) return@LaunchedEffect
        lastShown = text
        delay(2_400)
        PicaToast.dismiss(text)
    }

    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(SkinTokens.rowRadius),
            color = MiuixTheme.colorScheme.surfaceContainerHigh,
            contentColor = MiuixTheme.colorScheme.onSurface,
        ) {
            Text(
                text = lastShown,
                style = MiuixTheme.textStyles.body1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}
