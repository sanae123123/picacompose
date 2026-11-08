package com.picacompose.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 二级页的「容器变形」动画：点按钮 / 卡片时，那一块从原地长成整屏页面；返回时再缩回原处。
 *
 * 用法：外层套一个 [SharedTransitionLayout]，源（按钮、卡片）和目标（整页）用**同一个 key**
 * 各挂一次 [overlayBounds]，再把源和目标放进同一个 AnimatedContent 的两个 state 里，
 * 变形就自己串起来了。页面本体会铺满整屏，所以顺手把底部栏也盖住。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.overlayBounds(
    key: String,
    animatedVisibilityScope: AnimatedVisibilityScope,
): Modifier = Modifier.sharedBounds(
    sharedContentState = rememberSharedContentState(key = key),
    animatedVisibilityScope = animatedVisibilityScope,
)

/**
 * 配合 [overlayBounds] 用的进出动画：变形本身由 sharedBounds 负责，
 * 这里只压一层淡入淡出，免得两套动画打架。
 */
fun overlayPageFade(): ContentTransform =
    fadeIn(tween(durationMillis = 260, delayMillis = 80)) togetherWith
        fadeOut(tween(durationMillis = 90))
