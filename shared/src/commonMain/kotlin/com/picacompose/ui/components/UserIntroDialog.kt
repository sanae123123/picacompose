package com.picacompose.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.picacompose.data.pica
import com.picacompose.picaapi.model.UserProfile

/**
 * 用户简介弹窗：头像（点开看大图）+ 昵称 + 头衔/等级 + 简介。
 *
 * 「我的」页和评论区点别人的头像共用这一个，样式保持一致。
 */
@Composable
fun UserIntroDialog(
    name: String,
    avatarUrl: String?,
    slogan: String?,
    detail: String? = null,
    loading: Boolean = false,
    error: String? = null,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val pop = rememberDialogPop(onDismiss)
    var showAvatar by remember(avatarUrl) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = pop::dismiss,
        modifier = pop.modifier,
        title = { Text(text = "简介", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // 头像跟着简介一起滚；点开是全屏看图。
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "头像",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .clickable { showAvatar = true },
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (name.isNotBlank()) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                    )
                }
                if (!detail.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                when {
                    loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = colors.primary,
                        )
                    }
                    !error.isNullOrBlank() -> Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                    )
                    slogan.isNullOrBlank() -> Text(
                        text = "这个人还没写简介",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    else -> Text(
                        text = slogan,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = pop::dismiss) { Text("关闭") }
        },
    )

    if (showAvatar && avatarUrl != null) {
        PicaImageViewer(url = avatarUrl, onDismiss = { showAvatar = false })
    }
}

/**
 * 点别人的头像：先用列表里已有的昵称/头像顶上，再把 `users/{id}/profile` 拉回来补简介。
 *
 * 简介以服务端为准，不拿列表数据凑。
 */
@Composable
fun UserProfileDialog(
    userId: String,
    seedName: String? = null,
    seedAvatarUrl: String? = null,
    onDismiss: () -> Unit,
) {
    var profile by remember(userId) { mutableStateOf<UserProfile?>(null) }
    var loading by remember(userId) { mutableStateOf(true) }
    var error by remember(userId) { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        runCatching { pica.users.profileById(userId) }
            .onSuccess { profile = it }
            .onFailure { error = it.message ?: "资料没拉到" }
        loading = false
    }

    val loaded = profile
    UserIntroDialog(
        name = loaded?.name?.takeIf { it.isNotBlank() } ?: seedName.orEmpty(),
        avatarUrl = loaded?.avatar.url() ?: seedAvatarUrl,
        slogan = loaded?.slogan?.trim(),
        detail = loaded?.let { user ->
            listOfNotNull(
                "Lv.${user.level}",
                user.title?.takeIf { it.isNotBlank() },
                "经验 ${user.exp}",
            ).joinToString(" · ")
        },
        loading = loading,
        error = error,
        onDismiss = onDismiss,
    )
}
