package com.picacompose.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.picacompose.data.pica
import com.picacompose.picaapi.model.UserProfile
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.UserAvatar
import com.picacompose.ui.components.imagePickerSupported
import com.picacompose.ui.components.rememberImagePicker
import com.picacompose.ui.components.url
import kotlin.io.encoding.Base64
import kotlinx.coroutines.launch

/**
 * 编辑资料：头像、昵称、签名。
 *
 * 三项各走各的接口（`PUT users/avatar`、`PUT users/update-id`、`PUT users/profile`），
 * 保存时只发真正改过的那几项；改昵称要带上账号邮箱，拿不到邮箱就先别让改。
 */
@Composable
fun EditProfileContent(
    profile: UserProfile,
    onBack: () -> Unit,
    /** 保存成功之后：刷新外层那份资料，再关掉这一页。 */
    onSaved: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    // 进来先拉一份最新的：外层那份是登录 / 切页时拉的，可能已经过期。
    var current by remember { mutableStateOf(profile) }
    var name by remember { mutableStateOf(profile.name) }
    var slogan by remember { mutableStateOf(profile.slogan.orEmpty()) }
    // 用户动过输入框之后，晚到的资料就不再覆盖他的字。
    var edited by remember { mutableStateOf(false) }

    var avatarBytes by remember { mutableStateOf<ByteArray?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        catchingLoad { pica.users.profile() }.getOrNull()?.let { fresh ->
            current = fresh
            if (!edited) {
                name = fresh.name
                slogan = fresh.slogan.orEmpty()
            }
        }
    }

    val email = current.email?.takeIf { it.isNotBlank() }
    val trimmedName = name.trim()
    val trimmedSlogan = slogan.trim()
    val nameChanged = trimmedName != current.name
    val sloganChanged = trimmedSlogan != current.slogan.orEmpty().trim()
    val avatarChanged = avatarBytes != null
    val dirty = nameChanged || sloganChanged || avatarChanged
    // 昵称不能空着提交，服务端会直接拒。
    val nameBlank = trimmedName.isEmpty()

    val pickAvatar = rememberImagePicker { bytes -> avatarBytes = bytes }

    fun save() {
        if (saving || !dirty) return
        if (nameBlank) {
            error = "昵称不能是空的"
            return
        }
        scope.launch {
            saving = true
            error = null
            try {
                if (nameChanged) {
                    if (email == null) {
                        error = "这份资料里没有邮箱，昵称先改不了"
                    } else {
                        pica.users.updatePicaId(email = email, name = trimmedName)
                    }
                }
                if (sloganChanged) pica.users.updateSlogan(trimmedSlogan)
                if (avatarChanged) {
                    pica.users.updateAvatar(Base64.Default.encode(avatarBytes!!))
                }
                if (error == null) onSaved()
            } catch (e: Exception) {
                error = e.message ?: "保存没成功，稍后再试"
            } finally {
                saving = false
            }
        }
    }

    PageScaffold(
        modifier = modifier,
        header = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    PicaGlyph(
                        icon = PicaIcon.Back,
                        modifier = Modifier.size(22.dp),
                        tint = colors.onBackground,
                    )
                }
                Spacer(Modifier.width(6.dp))
                PageHeader(
                    title = "编辑资料",
                    action = {
                        TextButton(onClick = ::save, enabled = dirty && !saving && !nameBlank) {
                            Text(
                                text = if (saving) "保存中…" else "保存",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    },
                )
            }
        },
    ) {
        item {
            PicaCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp)) {
                        val picked = avatarBytes
                        if (picked != null) {
                            AsyncImage(
                                model = picked,
                                contentDescription = "新头像",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(64.dp).clip(CircleShape),
                            )
                        } else {
                            UserAvatar(
                                avatarUrl = current.avatar.url(),
                                frameUrl = current.character,
                                size = 64.dp,
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "头像",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSurface,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = when {
                                avatarBytes != null -> "已选新头像，保存后生效"
                                imagePickerSupported -> "从相册挑一张，会自动压到 512 像素"
                                else -> "这个平台暂时换不了头像"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    if (imagePickerSupported) {
                        TextButton(onClick = { pickAvatar() }) {
                            Text(text = "更换", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        item {
            PicaCard {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(20)
                        edited = true
                    },
                    label = { Text("昵称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (email == null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "这份资料里没有邮箱，昵称先改不了，签名不受影响。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = slogan,
                    onValueChange = {
                        slogan = it.take(120)
                        edited = true
                    },
                    label = { Text("签名") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(14.dp))
        }
        val message = error
        if (message != null) {
            item {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}
