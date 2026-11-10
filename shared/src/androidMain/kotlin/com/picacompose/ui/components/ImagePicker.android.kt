package com.picacompose.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

actual val imagePickerSupported: Boolean = true

@Composable
actual fun rememberImagePicker(onPicked: (ByteArray) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latest = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { readAvatarBytes(context, uri) }
            if (bytes != null) latest.value(bytes)
        }
    }
    return { launcher.launch("image/*") }
}

/**
 * 读相册里那张图，缩到最长边 512 再压成 JPEG。
 *
 * 手机拍的原图动辄几 MB，base64 之后更大，服务端不一定收；头像也用不上那么大。
 */
private fun readAvatarBytes(
    context: android.content.Context,
    uri: android.net.Uri,
): ByteArray? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val source = BitmapFactory.decodeStream(input) ?: return@runCatching null
        val longest = maxOf(source.width, source.height)
        val bitmap = if (longest <= 512) {
            source
        } else {
            val ratio = 512f / longest
            Bitmap.createScaledBitmap(
                source,
                (source.width * ratio).toInt().coerceAtLeast(1),
                (source.height * ratio).toInt().coerceAtLeast(1),
                true,
            )
        }
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            out.toByteArray()
        }
    }
}.getOrNull()
