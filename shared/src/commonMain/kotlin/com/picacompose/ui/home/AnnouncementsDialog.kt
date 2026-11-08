package com.picacompose.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.picacompose.data.AnnouncementStore
import com.picacompose.data.pica
import com.picacompose.picaapi.model.Announcement
import com.picacompose.ui.components.PicaImage
import androidx.compose.ui.layout.ContentScale.Companion.Crop
import com.picacompose.ui.components.PicaImageViewer
import com.picacompose.ui.components.url

/** 正文里的链接，抓出来单独标成可点的。 */
private val urlPattern = Regex("""https?://[^\s"'<>，。、）】]+""")

/**
 * 公告弹窗，仿官方那块公告板：顶图 + 标题 + 正文，底下「不再顯示 / 確定」。
 *
 * [initial] 有值时直接用（首页已经预热过），省一次请求。
 */
@Composable
fun AnnouncementsDialog(
    onDismiss: () -> Unit,
    initial: List<Announcement>? = null,
) {
    val colors = MaterialTheme.colorScheme
    val store = remember { AnnouncementStore() }
    var announcements by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    var index by remember { mutableStateOf(0) }
    // 点公告顶图放大看。
    var viewing by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (announcements == null) {
            runCatching { pica.meta.announcements(page = 1).docs }
                .onSuccess { docs -> announcements = docs.filterNot { it.id in store.dismissedIds() } }
                .onFailure { error = it.message ?: "公告没拉到" }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = colors.surface,
            tonalElevation = 3.dp,
        ) {
            val list = announcements
            when {
                error != null -> Column(Modifier.padding(24.dp)) {
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "稍后再试试。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }

                list == null -> Column(Modifier.padding(24.dp)) {
                    LoadingBlock("正在拉公告…")
                }

                list.isEmpty() -> Column(Modifier.padding(24.dp)) {
                    Text(
                        text = "暂时没有新公告。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("確定") }
                    }
                }

                else -> {
                    val current = list.getOrNull(index.coerceIn(0, list.lastIndex))
                    Column {
                        val thumb = current?.thumb.url()
                        if (thumb != null) {
                            PicaImage(
                                url = thumb,
                                contentDescription = current?.title,
                                contentScale = Crop,
                                alignment = Alignment.TopCenter,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2.8f)
                                    .clickable { viewing = thumb },
                            )
                        }
                        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp)) {
                            Text(
                                text = current?.title.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onSurface,
                            )
                            Spacer(Modifier.height(10.dp))
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 360.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                Text(
                                    text = linkify(current?.content.orEmpty().trim(), colors.primary),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(
                                    onClick = {
                                        current?.let { store.dismiss(it.id) }
                                        if (index < list.lastIndex) index++ else onDismiss()
                                    },
                                ) { Text("不再顯示") }
                                TextButton(
                                    onClick = {
                                        if (index < list.lastIndex) index++ else onDismiss()
                                    },
                                ) { Text("確定") }
                            }
                        }
                    }
                }
            }
        }
    }

    viewing?.let { url ->
        PicaImageViewer(url = url, onDismiss = { viewing = null })
    }
}

/** 把正文里的 URL 换成可点的链接，点了交给系统浏览器。 */
private fun linkify(text: String, linkColor: Color) = buildAnnotatedString {
    val styles = TextLinkStyles(
        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
    )
    var last = 0
    urlPattern.findAll(text).forEach { match ->
        append(text.substring(last, match.range.first))
        withLink(LinkAnnotation.Url(match.value, styles)) { append(match.value) }
        last = match.range.last + 1
    }
    append(text.substring(last))
}
