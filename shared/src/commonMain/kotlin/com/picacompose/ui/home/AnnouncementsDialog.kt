package com.picacompose.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.picacompose.data.pica
import com.picacompose.picaapi.model.Announcement
import com.picacompose.ui.components.PicaImageViewer
import com.picacompose.ui.components.PicaImage
import com.picacompose.ui.components.url

/** 正文里的链接，抓出来单独标成可点的。 */
private val urlPattern = Regex("""https?://[^\s"'<>，。、）】]+""")

/** 公告弹窗：顶图 + 标题 + 正文（链接可点），整块能滚。 */
@Composable
fun AnnouncementsDialog(onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var announcements by remember { mutableStateOf<List<Announcement>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // 点公告顶图放大看。
    var viewing by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { pica.meta.announcements(page = 1).docs }
            .onSuccess { announcements = it }
            .onFailure { error = it.message ?: "公告没拉到" }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("公告", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                val list = announcements
                when {
                    error != null -> Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                    )

                    list == null -> LoadingBlock("正在拉公告…")
                    list.isEmpty() -> Text(
                        text = "暂时没有公告。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )

                    else -> list.forEachIndexed { index, item ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 14.dp),
                                color = colors.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                        val thumb = item.thumb.url()
                        if (thumb != null) {
                            PicaImage(
                                url = thumb,
                                contentDescription = item.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { viewing = thumb },
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSurface,
                        )
                        val content = item.content.trim()
                        if (content.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = linkify(content, colors.primary),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )

    viewing?.let { url ->
        PicaImageViewer(url = url, onDismiss = { viewing = null })
    }
}

/** 把正文里的 URL 换成可点的链接，点了交给系统浏览器。 */
private fun linkify(text: String, linkColor: androidx.compose.ui.graphics.Color) =
    buildAnnotatedString {
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
