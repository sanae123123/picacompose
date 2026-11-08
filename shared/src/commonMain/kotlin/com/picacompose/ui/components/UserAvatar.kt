package com.picacompose.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * 圆头像 + 头像框。
 *
 * 框是接口里 `character` 字段直接给的图（骑士框、认证框、节日框、污字徽章……），
 * 中间是透明的，所以整张图叠在头像上就行。带框时头像收一圈，
 * 环形的框正好套在外面；没有框就画满。
 */
@Composable
fun UserAvatar(
    avatarUrl: String?,
    frameUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentDescription: String? = null,
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        PicaImage(
            url = avatarUrl,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize(if (frameUrl != null) 0.72f else 1f)
                .clip(CircleShape),
        )
        if (frameUrl != null) {
            AsyncImage(
                model = frameUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
