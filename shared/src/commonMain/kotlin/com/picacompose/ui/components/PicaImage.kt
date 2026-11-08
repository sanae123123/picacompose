package com.picacompose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.picacompose.picaapi.model.Thumbnail

/** 接口里的缩略图地址 = fileServer + path。 */
fun Thumbnail?.url(): String? {
    val base = this?.fileServer?.takeIf { it.isNotBlank() } ?: return null
    val path = this.path?.takeIf { it.isNotBlank() } ?: return null
    return if (path.startsWith("http")) path else base.trimEnd('/') + "/" + path.trimStart('/')
}

/** 网络图片：加载中/失败都留一块底色，不会塌成 0 高。 */
@Composable
fun PicaImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
