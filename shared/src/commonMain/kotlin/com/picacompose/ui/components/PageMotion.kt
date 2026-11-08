package com.picacompose.ui.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/**
 * 点按钮打开一整页时用的过渡：新页从右边整屏推进来，返回时再整屏滑回去。
 *
 * 官方 App 里「留言版」「消息」这类二级页就是盖满整屏、带推入动画的，
 * 这里用同一套。 [isPageOpen] 用来判断目标状态算不算「打开了一层新页面」，
 * 比如 `{ it != OverlayPage.None }` 或者 `{ it }`。
 *
 * 底下那一层不动：动画期间只让新页滑，省得两页都在动、看着晃。
 */
fun <S> AnimatedContentTransitionScope<S>.picaPagePush(
    isPageOpen: (S) -> Boolean,
): ContentTransform {
    val opening = isPageOpen(targetState)
    val enter = slideInHorizontally(tween(durationMillis = 320, easing = FastOutSlowInEasing)) { fullWidth ->
        fullWidth
    } + fadeIn(tween(durationMillis = 180))
    val exit = slideOutHorizontally(tween(durationMillis = 300, easing = FastOutSlowInEasing)) { fullWidth ->
        fullWidth
    } + fadeOut(tween(durationMillis = 200))
    return if (opening) enter togetherWith ExitTransition.None else EnterTransition.None togetherWith exit
}
