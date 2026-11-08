package com.picacompose.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.picacompose.data.pica
import com.picacompose.picaapi.model.Category
import com.picacompose.ui.components.PicaImage
import com.picacompose.ui.components.url

/**
 * 分类页：一块「嗶咔留言版」磁贴 + 接口返回的分类，两列网格排开。
 *
 * 留言版磁贴的封面用的是占位漫画（`愛嗶咔請留言`）的封面，跟官方那张本地图一个意思。
 */
@Composable
fun CategoriesContent(onOpenForum: () -> Unit = {}) {
    var reload by remember { mutableStateOf(0) }
    var categories by remember { mutableStateOf<List<Category>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var forumCover by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) {
        categories = null
        error = null
        runCatching { pica.meta.categories() }
            .onSuccess { categories = it }
            .onFailure { error = it.message ?: "分类没拉到" }
    }

    // 留言版没有自己的封面图，借用占位漫画的封面（接口里现成的）。
    LaunchedEffect(Unit) {
        runCatching { pica.comics.detail(FORUM_COMIC_ID).thumb.url() }
            .onSuccess { forumCover = it }
    }

    PageScaffold {
        item {
            PageHeader(title = "分类", subtitle = "站里的题材都在这里。")
            Spacer(Modifier.height(18.dp))
        }
        val list = categories
        when {
            error != null -> item { FailedBlock(error.orEmpty()) { reload++ } }
            list == null -> item { LoadingBlock("正在拉分类…") }
            list.isEmpty() -> item { EmptyBlock("接口没返回分类") }
            else -> {
                val tiles = buildList {
                    add(
                        CatTile(
                            cover = forumCover,
                            title = "嗶咔留言版",
                            subtitle = "愛嗶咔請留言",
                            selected = false,
                            onClick = onOpenForum,
                        ),
                    )
                    list.forEach { category ->
                        add(
                            CatTile(
                                cover = category.thumb.url(),
                                title = category.title,
                                subtitle = category.description?.takeIf { it.isNotBlank() && it != "未知" },
                                selected = category.title == selected,
                                onClick = { selected = category.title },
                            ),
                        )
                    }
                }
                val rows = tiles.chunked(2)
                items(rows.size) { index ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rows[index].forEach { tile ->
                            TileCard(tile = tile, modifier = Modifier.weight(1f))
                        }
                        if (rows[index].size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                selected?.let { title ->
                    item {
                        Text(
                            text = "选中的是「$title」，漫画列表下一步接上。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 网格里的一块磁贴，留言板和分类共用同一套样子。 */
private data class CatTile(
    val cover: String?,
    val title: String,
    val subtitle: String?,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun TileCard(
    tile: CatTile,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (tile.selected) colors.primaryContainer else colors.surface,
        tonalElevation = if (tile.selected) 0.dp else 1.dp,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.clickable(onClick = tile.onClick)) {
            PicaImage(
                url = tile.cover,
                contentDescription = tile.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f),
            )
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(
                    text = tile.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (tile.selected) colors.onPrimaryContainer else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = tile.subtitle
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (tile.selected) {
                            colors.onPrimaryContainer.copy(alpha = 0.8f)
                        } else {
                            colors.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
