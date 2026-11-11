package com.picacompose.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/** 弹窗进出场：打开时从 0.9 弹到 1 再淡入，关之前先把动画反过来播一遍。 */
class DialogPop internal constructor(
    val modifier: Modifier,
    private val close: () -> Unit,
) {
    /** 点外面、按返回、点关闭按钮都走这里；等动画播完才真的关。 */
    fun dismiss() = close()
}

/**
 * 给 `AlertDialog` / 自绘 `Dialog` 加自然弹出动画。
 *
 * ```kotlin
 * val pop = rememberDialogPop(onDismiss = { show = false })
 * PicaAlertDialog(onDismissRequest = pop::dismiss, modifier = pop.modifier, ...)
 * ```
 *
 * 自绘弹窗把 [DialogPop.modifier] 挂在里面那层 Surface 上，`Dialog.onDismissRequest` 用
 * [DialogPop.dismiss]，不然会直接消失、看不到收起的动画。
 */
@Composable
fun rememberDialogPop(onDismiss: () -> Unit): DialogPop {
    var closing by remember { mutableStateOf(false) }
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val open = appeared && !closing
    val scale = animateFloatAsState(
        targetValue = if (open) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "dialogPopScale",
    )
    val alpha = animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(durationMillis = 140),
        label = "dialogPopAlpha",
    )
    val modifier = remember(scale, alpha) {
        Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            this.alpha = alpha.value
        }
    }

    val latestDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(closing) {
        if (closing) {
            // 收起动画播完再从窗口里摘掉，不然弹窗是硬切没的。
            delay(180)
            latestDismiss()
        }
    }

    return remember(modifier) {
        DialogPop(modifier, close = { closing = true })
    }
}
