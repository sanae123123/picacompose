package com.picacompose.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.picacompose.picaapi.model.ComicSummary
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.PicaImage
import com.picacompose.ui.components.url
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 分类的本子列表：竖排单列，滚到底自动续页。
 *
 * pager 和 listState 由 [HomeShell] 持有：换分类时才换 pager，退出再进来不重拉第一页。
 */
@Composable
fun ComicListContent(
    title: String,
    pager: Pager<ComicSummary>,
    listState: LazyListState = rememberLazyListState(),
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    sort: String? = null,
    onSortChange: (String?) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    // 详情页还没做，点一本就先弹个说明。
    var teaser by remember { mutableStateOf<ComicSummary?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var showJump by remember { mutableStateOf(false) }

    LaunchedEffect(pager) {
        // 换排序会换一个 pager，回到顶部重新看。
        listState.scrollToItem(0)
        if (pager.items.isEmpty()) pager.loadNext()
    }

    // 滚到离底部还剩四五条的时候续页；pager.loading 挡住重复请求。
    LaunchedEffect(listState, pager) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (total > 0 && lastVisible >= total - 5 && pager.hasMore && pager.error == null) {
                    pager.loadNext()
                }
            }
    }

    PageScaffold(
        state = listState,
        modifier = modifier,
        onRefresh = { pager.reloadFirstPage() },
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
                Box(Modifier.weight(1f)) {
                    PageHeader(title = title)
                }
                // 官方列表右上角有个总数，这里也给一个。
                if (pager.total > 0) {
                    Text(
                        text = "${compactCount(pager.total)} 本",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                // 汉堡菜单：跳页 + 排序方式。
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(40.dp),
                    ) {
                        PicaGlyph(
                            icon = PicaIcon.Menu,
                            modifier = Modifier.size(22.dp),
                            tint = colors.onBackground,
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("跳页") },
                            leadingIcon = {
                                PicaGlyph(PicaIcon.ArrowRight, Modifier.size(18.dp), colors.onSurfaceVariant)
                            },
                            onClick = {
                                menuOpen = false
                                showJump = true
                            },
                        )
                        HorizontalDivider()
                        Text(
                            text = "排序方式",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                        )
                        ComicSort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                trailingIcon = {
                                    if (option.value == sort) {
                                        PicaGlyph(PicaIcon.Check, Modifier.size(18.dp), colors.primary)
                                    }
                                },
                                onClick = {
                                    menuOpen = false
                                    onSortChange(option.value)
                                },
                            )
                        }
                    }
                }
            }
        },
    ) {
        if (pager.items.isEmpty()) {
            item {
                when {
                    pager.error != null -> FailedBlock(pager.error.orEmpty()) {
                        scope.launch { pager.retry() }
                    }

                    pager.loading -> LoadingBlock("正在拉本子…")
                    else -> EmptyBlock("这个分类还没有本子")
                }
            }
        }
        items(count = pager.items.size, key = { pager.items[it].id }) { index ->
            val comic = pager.items[index]
            ComicListRow(comic = comic, onClick = { teaser = comic })
        }
        if (pager.items.isNotEmpty()) {
            item(key = "comic-list-footer") {
                ListFooter(
                    loading = pager.loading,
                    error = pager.error,
                    hasMore = pager.hasMore,
                    onRetry = { scope.launch { pager.retry() } },
                )
            }
        }
    }

    teaser?.let { comic ->
        ComicTeaserDialog(comic = comic, onDismiss = { teaser = null })
    }

    if (showJump) {
        PagerJumpDialog(
            pager = pager,
            onDismiss = { showJump = false },
            onJumped = { scope.launch { listState.scrollToItem(0) } },
        )
    }
}

/** 排序方式：`s` 参数跟官方一致，默認就是服务端原来的顺序。 */
private enum class ComicSort(val label: String, val value: String?) {
    Default("默認", null),
    Newest("最新", "dd"),
    Oldest("最早", "da"),
    MostLiked("最多愛心", "ld"),
    MostViewed("最多瀏覽", "vd"),
}

/** 单列里的一条：左边封面，右边标题 / 作者 / 数据，够一屏塞五六条。 */
@Composable
private fun ComicListRow(
    comic: ComicSummary,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            PicaImage(
                url = comic.thumb.url(),
                contentDescription = comic.title,
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = comic.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val author = comic.author?.takeIf { it.isNotBlank() }
                if (author != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${compactCount(comic.totalLikes)} 讚",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        text = "${compactCount(comic.totalViews)} 瀏覽",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        text = "${comic.pagesCount} 頁",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                val categories = comic.categories.take(3)
                if (categories.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.forEach { category ->
                            Text(
                                text = category,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (comic.finished) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "已完結",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
