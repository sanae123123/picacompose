package com.picacompose.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.picacompose.data.pica
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.PicaImage
import com.picacompose.ui.components.url
import com.picacompose.picaapi.model.Category

/** 分类页：拉真实分类接口，两列排开，卡片本身走懒加载。顶部挂留言板入口。 */
@Composable
fun CategoriesContent(onOpenForum: () -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    var reload by remember { mutableStateOf(0) }
    var categories by remember { mutableStateOf<List<Category>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) {
        categories = null
        error = null
        runCatching { pica.meta.categories() }
            .onSuccess { categories = it }
            .onFailure { error = it.message ?: "分类没拉到" }
    }

    PageScaffold {
        item {
            PageHeader(title = "分类", subtitle = "站里的题材都在这里。")
            Spacer(Modifier.height(14.dp))
            ForumEntryCard(onClick = onOpenForum)
            Spacer(Modifier.height(18.dp))
        }
        val list = categories
        when {
            error != null -> item { FailedBlock(error.orEmpty()) { reload++ } }
            list == null -> item { LoadingBlock() }
            list.isEmpty() -> item { EmptyBlock("接口没返回分类") }
            else -> {
                val rows = list.chunked(2)
                items(rows.size) { index ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rows[index].forEach { category ->
                            CategoryCard(
                                category = category,
                                selected = category.title == selected,
                                onClick = { selected = category.title },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rows[index].size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                selected?.let { title ->
                    item {
                        Text(
                            text = "选中的是「$title」，漫画列表下一步接上。",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 分类页顶部的留言板入口，官方 App 把它摆在固定磁贴里。 */
@Composable
private fun ForumEntryCard(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    PicaCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.medium, color = colors.primaryContainer) {
                PicaGlyph(
                    icon = PicaIcon.Notice,
                    modifier = Modifier.padding(10.dp).size(22.dp),
                    tint = colors.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "嗶咔留言版",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "全站留言都在这儿，和评论区同一套。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PicaGlyph(
                icon = PicaIcon.ArrowRight,
                modifier = Modifier.size(18.dp),
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoryCard(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) colors.primaryContainer else colors.surface,
        tonalElevation = if (selected) 0.dp else 1.dp,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.clickable(onClick = onClick)) {
            PicaImage(
                url = category.thumb.url(),
                contentDescription = category.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f),
            )
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val description = category.description?.takeIf { it.isNotBlank() && it != "未知" }
                if (!description.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) {
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
