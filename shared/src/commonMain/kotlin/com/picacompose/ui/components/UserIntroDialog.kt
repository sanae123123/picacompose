package com.picacompose.ui.components

import com.picacompose.ui.components.PicaCircularProgress
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import com.picacompose.ui.components.PicaAlertDialog
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import com.picacompose.ui.components.PicaTextButton
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
    title: String? = null,
    detail: String? = null,
    frameUrl: String? = null,
    loading: Boolean = false,
    error: String? = null,
    onDismiss: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val pop = rememberDialogPop(onDismiss)
    var showAvatar by remember(avatarUrl) { mutableStateOf(false) }

    PicaAlertDialog(
        onDismissRequest = pop::dismiss,
        modifier = pop.modifier,
        title = { Text(text = "简介", style = MiuixTheme.textStyles.title4) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // 头像跟着简介一起滚；点开是全屏看图。
                if (avatarUrl != null) {
                    UserAvatar(
                        avatarUrl = avatarUrl,
                        frameUrl = frameUrl,
                        size = 88.dp,
                        contentDescription = "头像",
                        modifier = Modifier.clickable { showAvatar = true },
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (name.isNotBlank() || !title.isNullOrBlank()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = name,
                            style = MiuixTheme.textStyles.subtitle,
                            color = colors.onSurface,
                        )
                        UserTitleBadge(title)
                    }
                }
                if (!detail.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = detail,
                        style = MiuixTheme.textStyles.footnote2,
                        color = colors.onSurfaceSecondary,
                    )
                }
                Spacer(Modifier.height(12.dp))
                when {
                    !slogan.isNullOrBlank() -> Text(
                        text = slogan,
                        style = MiuixTheme.textStyles.body1,
                        color = colors.onSurfaceSecondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    loading -> PicaCircularProgress(
color = colors.primary,
modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    !error.isNullOrBlank() -> Text(
                        text = error,
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.error,
                    )
                    else -> Text(
                        text = "这个人还没写简介",
                        style = MiuixTheme.textStyles.body1,
                        color = colors.onSurfaceSecondary,
                    )
                }
            }
        },
        confirmButton = {
            PicaTextButton(onClick = pop::dismiss) { Text("关闭") }
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
    seedTitle: String? = null,
    seedSlogan: String? = null,
    seedLevel: Int? = null,
    seedExp: Int? = null,
    seedGender: String? = null,
    seedFrame: String? = null,
    onDismiss: () -> Unit,
) {
    var profile by remember(userId) { mutableStateOf<UserProfile?>(null) }
    var error by remember(userId) { mutableStateOf<String?>(null) }
    val loaded = profile

    LaunchedEffect(userId) {
        runCatching { pica.users.profileById(userId) }
            .onSuccess { profile = it }
            .onFailure { error = it.message ?: "资料没拉到" }
    }

    // 列表里本来就带着昵称/头像/称号/简介，先用它铺满，服务端资料回来再覆盖。
    val slogan = loaded?.slogan?.trim() ?: seedSlogan?.trim()
    val level = loaded?.level ?: seedLevel
    val exp = loaded?.exp ?: seedExp
    val gender = loaded?.gender ?: seedGender
    val frame = loaded?.character ?: seedFrame

    UserIntroDialog(
        name = loaded?.name?.takeIf { it.isNotBlank() } ?: seedName.orEmpty(),
        avatarUrl = loaded?.avatar.url() ?: seedAvatarUrl,
        frameUrl = frame,
        title = loaded?.title?.takeIf { it.isNotBlank() } ?: seedTitle,
        slogan = slogan,
        detail = userDetailLine(gender = gender, level = level, exp = exp),
        loading = loaded == null && slogan.isNullOrBlank() && error == null,
        error = error,
        onDismiss = onDismiss,
    )
}
