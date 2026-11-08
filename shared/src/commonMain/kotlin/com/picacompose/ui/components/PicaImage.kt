package com.picacompose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.picacompose.picaapi.PicaImage
import com.picacompose.picaapi.model.Thumbnail

/** 接口里的缩略图地址，拼接规则见 [PicaImage.url]。 */
fun Thumbnail?.url(imageServer: String? = null): String? = PicaImage.url(this, imageServer)

/** 网络图片：加载中/失败都留一块底色，不会塌成 0 高。 */
@Composable
fun PicaImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = contentScale,
                alignment = alignment,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
