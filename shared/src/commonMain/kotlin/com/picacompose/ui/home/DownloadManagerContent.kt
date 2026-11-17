package com.picacompose.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.picacompose.data.ComicDownloader
import com.picacompose.data.DownloadStatus
import com.picacompose.data.DownloadedBook
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.PicaIconButton
import com.picacompose.ui.components.PicaLinearProgress
import com.picacompose.ui.components.PicaSurface
import com.picacompose.ui.components.PicaTextButton
import com.picacompose.ui.theme.SkinTokens

/**
 * 下载管理：缓存过的本子、正在下的本子都在这儿。
 *
 * 列表本身扫一次盘（读几份元数据），进度从 [ComicDownloader.status] 实时拿；
 * 取消、重试、删除之后重新扫一遍。
 */
@Composable
fun DownloadManagerContent(
    onBack: () -> Unit,
    onOpenComic: (comicId: String, title: String, key: String) -> Unit = { _, _, _ -> },
    comicRowModifier: @Composable (String) -> Modifier = { Modifier },
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val statuses by ComicDownloader.status.collectAsState()
    var books by remember { mutableStateOf<List<DownloadedBook>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    suspend fun reload() {
        books = ComicDownloader.downloads()
        loaded = true
    }

    LaunchedEffect(Unit) { reload() }

    PageScaffold(
        state = listState,
        modifier = modifier,
        onRefresh = { reload() },
        header = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PicaIconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    PicaGlyph(
                        icon = PicaIcon.Back,
                        modifier = Modifier.size(22.dp),
                        tint = colors.onBackground,
                    )
                }
                Spacer(Modifier.width(6.dp))
                PageHeader(title = "下载管理")
            }
        },
    ) {
        if (loaded && books.isEmpty()) {
            item { EmptyBlock("还没缓存过本子。详情页右上角那颗下载图标能把整本存下来。") }
        }
        items(books, key = { it.comicId }) { book ->
            val status = statuses[book.comicId] ?: book.status
            val rowKey = "dl-${book.comicId}"
            DownloadRow(
                book = book,
                status = status,
                modifier = comicRowModifier(rowKey),
                onClick = { onOpenComic(book.comicId, book.title, rowKey) },
                onCancel = {
                    ComicDownloader.cancel(book.comicId)
                    scope.launch { reload() }
                },
                onRetry = {
                    ComicDownloader.start(book.comicId, book.title)
                    scope.launch { reload() }
                },
                onDelete = {
                    ComicDownloader.delete(book.comicId)
                    scope.launch { reload() }
                },
            )
        }
    }
}

@Composable
private fun DownloadRow(
    book: DownloadedBook,
    status: DownloadStatus,
    onClick: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    PicaSurface(
        shape = RoundedCornerShape(SkinTokens.rowRadius),
        color = colors.surface,
        modifier = modifier.fillMaxWidth().padding(bottom = 12.dp).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PicaSurface(
                shape = RoundedCornerShape(10.dp),
                color = colors.secondaryContainer,
                modifier = Modifier.width(54.dp).aspectRatio(3f / 4f),
            ) {
                if (book.cover != null) {
                    AsyncImage(
                        model = book.cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MiuixTheme.textStyles.body1,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(4.dp))
                when (status) {
                    is DownloadStatus.Running -> {
                        Text(
                            text = if (status.total > 0) {
                                "缓存中 ${status.done}/${status.total} 页"
                            } else {
                                "缓存中…"
                            },
                            style = MiuixTheme.textStyles.footnote1,
                            color = colors.onSurfaceSecondary,
                        )
                        if (status.total > 0) {
                            Spacer(Modifier.size(6.dp))
                            PicaLinearProgress(
                                progress = status.done.toFloat() / status.total,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    is DownloadStatus.Preparing -> Text(
                        text = "准备中…",
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceSecondary,
                    )
                    is DownloadStatus.Completed -> Text(
                        text = "已缓存 · ${sizeText(status.bytes)}" +
                            if (book.pages > 0) " · ${book.pages} 页" else "",
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceSecondary,
                    )
                    is DownloadStatus.Failed -> Text(
                        text = status.message,
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    DownloadStatus.Idle -> Text(
                        text = "还没缓存",
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceSecondary,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            when (status) {
                is DownloadStatus.Preparing, is DownloadStatus.Running -> {
                    PicaTextButton(onClick = onCancel) {
                        Text("取消", style = MiuixTheme.textStyles.button)
                    }
                }
                is DownloadStatus.Completed -> {
                    PicaTextButton(onClick = onDelete) {
                        Text("删除", style = MiuixTheme.textStyles.button)
                    }
                }
                is DownloadStatus.Failed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PicaTextButton(onClick = onRetry) {
                            Text("重试", style = MiuixTheme.textStyles.button)
                        }
                        PicaTextButton(onClick = onDelete) {
                            Text("删除", style = MiuixTheme.textStyles.button)
                        }
                    }
                }
                DownloadStatus.Idle -> Unit
            }
        }
    }
}

/** 大小按 MB / GB 报，几十万字节的数看得清一点。 */
private fun sizeText(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1024) {
        val gb = mb / 1024.0
        (gb * 10).toInt().let { "${it / 10}.${it % 10} GB" }
    } else {
        (mb * 10).toInt().let { "${it / 10}.${it % 10} MB" }
    }
}
