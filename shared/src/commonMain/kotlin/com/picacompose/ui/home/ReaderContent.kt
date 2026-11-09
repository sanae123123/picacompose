package com.picacompose.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Size
import com.picacompose.data.pica
import com.picacompose.picaapi.model.Episode
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.url
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 提前把后面几张图抓下来，手指划过去时是现成的。 */
private const val PRELOAD_AHEAD = 4

/**
 * 阅读器：整屏黑底，图片一张挨一张竖着排，滚到底自动续页。
 *
 * 点一下画面收起 / 放出上下两条工具条；工具条上能返回详情、翻上一話 / 下一話、看进度。
 * 它是整页而不是弹窗：从章節那一行撑开成整屏（和别的整页共用同一套变形），
 * 退回去时详情页的状态提到外层存着，章節列表还停在刚才点的那一行。
 */
@Composable
fun ReaderContent(
    comicId: String,
    comicTitle: String,
    episodes: List<Episode>,
    startOrder: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var order by remember(startOrder) { mutableIntStateOf(startOrder) }
    val pager = remember(comicId, order) {
        Pager(
            load = { page -> pica.comics.pages(comicId, order, page).pages },
            itemKey = { it.id },
        )
    }
    // 换一話就从头看。
    val listState = remember(order) { LazyListState() }
    var chrome by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val platformContext = LocalPlatformContext.current

    LaunchedEffect(pager) {
        if (pager.items.isEmpty()) pager.loadNext()
    }

    // 预先抓下面几张（连下一批也有）：图片请求都按原尺寸走，命中同一份内存缓存。
    LaunchedEffect(listState, pager, platformContext) {
        val loader = SingletonImageLoader.get(platformContext)
        snapshotFlow {
            listState.firstVisibleItemIndex to pager.items.size
        }
            .distinctUntilChanged()
            .collect { (index, size) ->
                if (size == 0) return@collect
                val until = (index + PRELOAD_AHEAD).coerceAtMost(size - 1)
                for (i in index + 1..until) {
                    val url = pager.items[i].media.url() ?: continue
                    loader.enqueue(
                        ImageRequest.Builder(platformContext)
                            .data(url)
                            .size(Size.ORIGINAL)
                            .build(),
                    )
                }
            }
    }

    // 离底部还剩两张就续页。
    LaunchedEffect(listState, pager) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (total > 0 && lastVisible >= total - 3 && pager.hasMore && pager.error == null) {
                    pager.loadNext()
                }
            }
    }

    val prevOrder = episodes.filter { it.order < order }.maxOfOrNull { it.order }
    val nextOrder = episodes.filter { it.order > order }.minOfOrNull { it.order }
    val title = episodes.firstOrNull { it.order == order }?.title.orEmpty()
    val shown = (listState.firstVisibleItemIndex + 1).coerceAtLeast(1)
    val totalPages = pager.total

    Box(modifier.fillMaxSize().background(Color.Black)) {
            // 内容本身不拦点击，点一下切换工具条。
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { chrome = !chrome })
                    },
            ) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(pager.items, key = { it.id }) { page ->
                        val url = page.media.url()
                        if (url != null) {
                            ReaderPage(url = url, platformContext = platformContext)
                        }
                    }
                    if (pager.items.isEmpty()) {
                        item(key = "reader-state") {
                            ReaderState(
                                loading = pager.loading,
                                error = pager.error,
                                onRetry = { scope.launch { pager.retry() } },
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = chrome,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChromeButton(onClick = onDismiss) {
                        PicaGlyph(PicaIcon.Back, Modifier.size(22.dp), Color.White)
                    }
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = title.ifBlank { "第 $order 話" },
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = comicTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = if (totalPages > 0) "$shown / $totalPages" else "$shown",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = chrome,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EpisodeButton(
                        label = "上一話",
                        enabled = prevOrder != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        prevOrder?.let { order = it }
                    }
                    EpisodeButton(
                        label = "下一話",
                        enabled = nextOrder != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        nextOrder?.let { order = it }
                    }
                }
        }
    }
}

/**
 * 一页图：还没来时占一块 3:4 的转圈，图片落地后按真实尺寸重新测量。
 *
 * 不这样的话，未加载的项高度是 0，往下划时看不出还剩多少、也容易一划就跳过去。
 */
@Composable
private fun ReaderPage(
    url: String,
    platformContext: PlatformContext,
) {
    var state by remember(url) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }
    var retryTick by remember(url) { mutableIntStateOf(0) }
    // 和预抓用同一套请求参数，缓存才对得上。
    val request = remember(url) {
        ImageRequest.Builder(platformContext)
            .data(url)
            .size(Size.ORIGINAL)
            .build()
    }
    val failed = state is AsyncImagePainter.State.Error

    Box(Modifier.fillMaxWidth()) {
        if (state is AsyncImagePainter.State.Empty ||
            state is AsyncImagePainter.State.Loading ||
            failed
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                contentAlignment = Alignment.Center,
            ) {
                if (failed) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "这张没加载出来",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.16f),
                            // 先把状态拨回「还没加载」，否则下面那段不会重新挂图片。
                            modifier = Modifier.clickable {
                                state = AsyncImagePainter.State.Empty
                                retryTick++
                            },
                        ) {
                            Text(
                                text = "点一下重试",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                } else {
                    CircularProgressIndicator(
                        color = Color.White.copy(alpha = 0.85f),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
        // 失败时把它撤掉，省得一直挂着一个看不见的请求；重试时换个 key 重新来一遍。
        if (!failed) {
            key(retryTick) {
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    onState = { state = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 工具条上的圆形按钮。 */
@Composable
private fun ChromeButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** 底部翻話按钮：能翻就白底字，不能翻就灰掉。 */
@Composable
private fun EpisodeButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (enabled) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f),
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
            )
        }
    }
}

/** 图片还没来时：黑底上的加载 / 失败提示。 */
@Composable
private fun ReaderState(
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            error != null -> {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.16f),
                    modifier = Modifier.clickable(onClick = onRetry),
                ) {
                    Text(
                        text = "再试一次",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
            }

            loading -> Text(
                text = "正在拉图片…",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
            )

            else -> Text(
                text = "这話没有图片",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}
