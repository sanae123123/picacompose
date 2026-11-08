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
import com.picacompose.ui.components.PicaImage
import com.picacompose.ui.components.url
import com.picacompose.picaapi.model.Category

/** 分类页：拉真实分类接口，两列排开，卡片本身走懒加载。 */
@Composable
fun CategoriesContent() {
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
