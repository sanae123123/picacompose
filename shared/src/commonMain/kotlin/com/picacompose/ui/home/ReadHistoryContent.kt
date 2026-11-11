package com.picacompose.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.picacompose.data.ReadingProgress
import com.picacompose.data.readingProgressStore
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.PicaImage

/**
 * 浏览历史：本机记的「翻过哪些本子、读到第几话」。
 *
 * 官方接口没有这个，所以由详情页打开时顺手记一条（见 [ReadingProgressStore.recordOpen]），
 * 阅读器再更新读到第几页。最近打开的排最前面。
 */
@Composable
fun ReadHistoryContent(
    onBack: () -> Unit,
    onOpenComic: (comicId: String, title: String, key: String) -> Unit = { _, _, _ -> },
    comicRowModifier: @Composable (String) -> Modifier = { Modifier },
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    // 清空之后换个版本号，列表立刻跟着变空。
    var version by remember { mutableIntStateOf(0) }
    val history = remember(version) { readingProgressStore.all() }

    PageScaffold(
        state = listState,
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
                    title = "浏览历史",
                    action = if (history.isNotEmpty()) {
                        {
                            TextButton(
                                onClick = {
                                    readingProgressStore.clearAll()
                                    version += 1
                                },
                            ) {
                                Text("清空", style = MiuixTheme.textStyles.button)
                            }
                        }
                    } else {
                        null
                    },
                )
            }
        },
    ) {
        if (history.isEmpty()) {
            item { EmptyBlock("还没翻过本子，打开一本就会记在这里") }
        }
        items(history, key = { it.comicId }) { item ->
            val key = "his-${item.comicId}"
            HistoryRow(
                item = item,
                modifier = comicRowModifier(key),
                onClick = { onOpenComic(item.comicId, item.title.orEmpty(), key) },
            )
        }
    }
}

@Composable
private fun HistoryRow(
    item: ReadingProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colors.surface,
        modifier = modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            PicaImage(
                url = item.cover?.takeIf { it.isNotBlank() },
                contentDescription = item.title,
                modifier = Modifier.width(72.dp).aspectRatio(3f / 4f).clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title?.takeIf { it.isNotBlank() } ?: "未命名本子",
                    style = MiuixTheme.textStyles.subtitle,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val author = item.author?.takeIf { it.isNotBlank() }
                if (author != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = author,
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (item.order > 0) {
                        val pages = if (item.pages > 0) " · 第 ${item.page + 1}/${item.pages} 页" else ""
                        "看到第 ${item.order} 话$pages"
                    } else {
                        "打开过，还没开始读"
                    },
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.primary,
                )
            }
        }
    }
}
