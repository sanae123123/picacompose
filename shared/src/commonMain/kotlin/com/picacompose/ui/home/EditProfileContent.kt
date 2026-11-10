package com.picacompose.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.picacompose.data.pica
import com.picacompose.picaapi.model.UserProfile
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.rememberDialogPop
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
    // 没保存成功的原因，攒起来弹窗说（贴在列表底下会被输入法挡住）。
    var failure by remember { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current

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
            failure = "昵称不能是空的。"
            return
        }
        keyboard?.hide()
        scope.launch {
            saving = true
            failure = null
            // 三项各走各的接口，一项失败不影响另外两项，最后把没成功的列出来。
            val problems = mutableListOf<String>()
            if (nameChanged) {
                if (email == null) {
                    problems += "昵称：这份资料里没有邮箱，改不了。"
                } else {
                    try {
                        pica.users.updatePicaId(email = email, name = trimmedName)
                    } catch (e: Exception) {
                        problems += "昵称：${friendlySaveError(e)}"
                    }
                }
            }
            if (sloganChanged) {
                try {
                    pica.users.updateSlogan(trimmedSlogan)
                } catch (e: Exception) {
                    problems += "签名：${e.message ?: "提交失败"}"
                }
            }
            if (avatarChanged) {
                try {
                    pica.users.updateAvatar(Base64.Default.encode(avatarBytes!!))
                } catch (e: Exception) {
                    problems += "头像：${e.message ?: "提交失败"}"
                }
            }
            if (problems.isEmpty()) {
                onSaved()
            } else {
                // 成功的那几项先认下来，别让「再点一次保存」把它们又发一遍。
                catchingLoad { pica.users.profile() }.getOrNull()?.let { fresh ->
                    current = fresh
                    if (!nameChanged || fresh.name == trimmedName) name = fresh.name
                    slogan = fresh.slogan.orEmpty()
                    avatarBytes = null
                }
                failure = problems.joinToString("\n")
            }
            saving = false
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
                    // 不在客户端截：以前偷偷截到 20 字，用户看不见截断，保存下去就把原昵称盖了。
                    onValueChange = {
                        name = it
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
                    // 同上：长简介原样提交，能存多少让服务端说了算（存不下会弹窗报原因）。
                    onValueChange = {
                        slogan = it
                        edited = true
                    },
                    label = { Text("签名") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(14.dp))
        }
    }

    // 失败的说明用弹窗：列表底下那条会被输入法盖住，等于没说。
    failure?.let { text ->
        val pop = rememberDialogPop(onDismiss = { failure = null })
        AlertDialog(
            onDismissRequest = pop::dismiss,
            modifier = pop.modifier,
            title = { Text(text = "没保存成功", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = pop::dismiss) { Text("知道了") }
            },
        )
    }
}

/**
 * 把服务端那串英文翻成人话。
 *
 * 改昵称被拒最常见的是「刚改过，短时间不让再改」，服务端只回一句英文，直接摆给用户
 * 看不懂，所以认出来就补一句解释；认不出来就原样给，至少能拿去搜。
 */
private fun friendlySaveError(e: Exception): String {
    val raw = e.message?.trim().orEmpty()
    val lower = raw.lowercase()
    val looksLikeRenameDenied =
        (lower.contains("nickname") || lower.contains("picaid") || lower.contains("nick")) ||
            raw.contains("昵称")
    return when {
        raw.isBlank() -> "提交失败，网络或服务端出了点问题。"
        looksLikeRenameDenied && (lower.contains("edit") || lower.contains("change") || raw.contains("编辑") || raw.contains("修改")) ->
            "服务端说昵称已经改过一次，短时间内不能再改，签名和头像还是能保存。\n\n（原文：$raw）"
        else -> raw
    }
}
