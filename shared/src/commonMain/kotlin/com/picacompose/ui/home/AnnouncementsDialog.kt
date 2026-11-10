package com.picacompose.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.picacompose.data.pica
import com.picacompose.picaapi.model.Announcement
import com.picacompose.ui.components.PicaImage
import com.picacompose.ui.components.PicaImageViewer
import com.picacompose.ui.components.rememberDialogPop
import com.picacompose.ui.components.url

/** 正文里的链接，抓出来单独标成可点的。 */
private val urlPattern = Regex("""https?://[^\s"'<>，。、）】]+""")

/**
 * 公告弹窗：首图 + 标题 + 正文一起滚，底下「公告列表 / 确定」。
 *
 * 只有用户自己点「公告」才打开，不会自己冒出来。
 */
@Composable
fun AnnouncementsDialog(onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pop = rememberDialogPop(onDismiss)
    var announcements by remember { mutableStateOf<List<Announcement>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var index by remember { mutableStateOf(0) }
    // 打开公告列表面板。
    var showList by remember { mutableStateOf(false) }
    // 点公告首图放大看。
    var viewing by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    // 换一条公告就回到最顶上。
    LaunchedEffect(index) { scrollState.scrollTo(0) }

    LaunchedEffect(Unit) {
        runCatching { pica.meta.announcements(page = 1).docs }
            .onSuccess { announcements = it }
            .onFailure { error = it.message ?: "公告没拉到" }
    }

    Dialog(onDismissRequest = pop::dismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = colors.surface,
            tonalElevation = 3.dp,
            modifier = pop.modifier,
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
                        text = "暂时没有公告。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = pop::dismiss) { Text("确定") }
                    }
                }

                showList -> AnnouncementList(
                    items = list,
                    current = index,
                    onPick = {
                        index = it
                        showList = false
                    },
                    onClose = { showList = false },
                )

                else -> {
                    val current = list.getOrNull(index.coerceIn(0, list.lastIndex))
                    Column {
                        // 首图和标题跟着正文一起滚，所以图不用裁成一条横幅。
                        Column(
                            modifier = Modifier
                                .heightIn(max = 520.dp)
                                .verticalScroll(scrollState),
                        ) {
                            val thumb = current?.thumb.url()
                            if (thumb != null) {
                                AnnouncementBanner(url = thumb, onClick = { viewing = thumb })
                            }
                            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 16.dp)) {
                                Text(
                                    text = current?.title.orEmpty(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.onSurface,
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = linkify(current?.content.orEmpty().trim(), colors.primary),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 12.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { showList = true }) { Text("公告列表") }
                            TextButton(onClick = pop::dismiss) { Text("确定") }
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

/** 公告列表面板：缩略图 + 标题，点一条就看那条。 */
@Composable
private fun AnnouncementList(
    items: List<Announcement>,
    current: Int,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column {
        Text(
            text = "公告列表",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 6.dp),
        )
        LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
            itemsIndexed(items) { i, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(i) }
                        .padding(horizontal = 22.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PicaImage(
                        url = item.thumb.url(),
                        contentDescription = item.title,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (i == current) colors.primary else colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.content.trim().lineSequence().firstOrNull().orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onClose) { Text("关闭") }
        }
    }
}

/** 公告首图：按原图比例铺满宽度，不裁切，点了看大图。 */
@Composable
private fun AnnouncementBanner(url: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var aspect by remember(url) { mutableStateOf(0f) }
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        alignment = Alignment.TopCenter,
        onSuccess = { state ->
            val size = state.painter.intrinsicSize
            if (size.width > 0f && size.height > 0f) aspect = size.width / size.height
        },
        modifier = Modifier
            .fillMaxWidth()
            .then(if (aspect > 0f) Modifier.aspectRatio(aspect) else Modifier.height(200.dp))
            .background(colors.surfaceVariant)
            .clickable(onClick = onClick),
    )
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
