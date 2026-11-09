package com.picacompose.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.picacompose.data.pica
import com.picacompose.picaapi.model.ComicSummary
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon

/**
 * 「隨機本子」分区。
 *
 * 官方这里是 4 个哨兵分区之一：磁贴写死 index 7，扇进 `ComicListFragment("CATEGORY_RANDOM")`，
 * 由 fragment 的 `cR()` 分支走 `GET comics/random`。所以这块和普通分类列表不一样——
 * 接口一次给一批，没有分页、没有排序菜单、也没有翻页栏，想换一批只能重新请求。
 * 这里照这个行为做：一屏列表 + 右上角「換一批」。
 */
@Composable
fun RandomComicsContent(
    listState: LazyListState = rememberLazyListState(),
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenComic: (ComicSummary) -> Unit = {},
    comicRowModifier: @Composable (ComicSummary) -> Modifier = { Modifier },
) {
    val colors = MaterialTheme.colorScheme
    var comics by remember { mutableStateOf<List<ComicSummary>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(tick) {
        loading = true
        error = null
        runCatching { pica.comics.random() }
            .onSuccess { comics = it }
            .onFailure { error = it.message ?: "随机本子没拉到" }
        loading = false
        // 换一批回到顶部。
        listState.scrollToItem(0)
    }

    PageScaffold(
        state = listState,
        modifier = modifier,
        onRefresh = { tick++ },
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
                    PageHeader(title = "隨機本子")
                }
                Text(
                    text = "換一批",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { tick++ }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
        },
    ) {
        val list = comics
        if (list.isNullOrEmpty()) {
            item(key = "random-state") {
                when {
                    error != null -> FailedBlock(error.orEmpty()) { tick++ }
                    loading -> LoadingBlock("正在抽本子…")
                    else -> EmptyBlock("这次没抽到本子")
                }
            }
        }
        if (list != null) {
            items(count = list.size, key = { list[it].id }) { index ->
                val comic = list[index]
                ComicListRow(
                    comic = comic,
                    onClick = { onOpenComic(comic) },
                    modifier = comicRowModifier(comic),
                )
            }
        }
        // 随机就一批，没有下一页；底部留一句说明，免得一直往下滑。
        if (!list.isNullOrEmpty() && !loading && error == null) {
            item(key = "random-footer") {
                Text(
                    text = "就这些，想换点上面的「換一批」",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 18.dp),
                )
            }
        }
    }
}
