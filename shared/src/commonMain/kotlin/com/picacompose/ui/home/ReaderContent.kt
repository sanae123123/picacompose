package com.picacompose.ui.home

import com.picacompose.ui.components.PicaSurface
import com.picacompose.ui.components.PicaCircularProgress
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.height
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
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.exp

/** 提前把后面几张图抓下来，手指划过去时是现成的。 */
private const val PRELOAD_AHEAD = 6

/** 往回翻也留两张：看完回头找细节不至于重新下。 */
private const val PRELOAD_BEHIND = 2

/** 最多放到几倍。再大就糊了，也没谁真看那么细。 */
private const val MAX_ZOOM = 5f

/**
 * 阅读器：整屏黑底，图片一张挨一张竖着排，滚到底自动续页。
 *
 * 点一下画面收起 / 放出上下两条工具条；工具条上能返回详情、翻上一话 / 下一话、看进度。
 * 它是整页而不是弹窗：从章节那一行撑开成整屏（和别的整页共用同一套变形），
 * 退回去时详情页的状态提到外层存着，章节列表还停在刚才点的那一行。
 */
@Composable
fun ReaderContent(
    comicId: String,
    comicTitle: String,
    episodes: List<Episode>,
    startOrder: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** 上次读到的那一页（换一话就从头看）。 */
    startPage: Int = 0,
    /** 读到哪里就报一下，外面存起来。 */
    onProgress: (order: Int, page: Int, pages: Int) -> Unit = { _, _, _ -> },
) {
    var order by remember(startOrder) { mutableIntStateOf(startOrder) }
    val pager = remember(comicId, order) {
        Pager(
            load = { page -> pica.comics.pages(comicId, order, page).pages },
            itemKey = { it.id },
        )
    }
    // 换一话就从头看；同一话重新进来时滚回上次那一页。
    val listState = remember(order) { LazyListState() }
    var chrome by remember { mutableStateOf(true) }
    // 双指缩放：整块画面一起放大，图片本身还是原图，放大后不糊。
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var flingJob by remember { mutableStateOf<Job?>(null) }
    // 捏合缩放的累计量，用来过滤两指斜划时的抖动。
    var zoomAccum by remember { mutableFloatStateOf(1f) }
    // 只在「有没有放大」翻转时才重组，缩放过程中每帧都读 scale 会把整个列表重组一遍。
    val zoomed by remember { derivedStateOf { scale > 1f } }
    val scope = rememberCoroutineScope()
    val platformContext = LocalPlatformContext.current

    // 只有刚进来那次才恢复进度；同一话里换上下话就从头看。
    var resuming by remember { mutableStateOf(startPage > 0) }

    // 换一话回到 1 倍，别把上一话的缩放带过去。
    LaunchedEffect(order) {
        flingJob?.cancel()
        scale = 1f
        offset = Offset.Zero
    }

    LaunchedEffect(pager) {
        if (pager.items.isEmpty()) {
            val resumeTo = if (resuming && order == startOrder) startPage else 0
            if (resumeTo > 0) {
                // 接着上次读的那一话：先把前面几页补齐，再滚到那一页。
                var guard = 0
                while (pager.items.size <= resumeTo && pager.hasMore && guard < 40) {
                    pager.loadNext()
                    guard++
                }
                if (pager.items.size > resumeTo) listState.scrollToItem(resumeTo)
            } else {
                pager.loadNext()
            }
        }
        // 恢复完再放开写进度，别刚进来就把上次的位置冲成第 1 页。
        resuming = false
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
                val from = (index - PRELOAD_BEHIND).coerceAtLeast(0)
                for (i in from..until) {
                    if (i == index) continue
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

    // 读到哪一页了：滚停的时候往上报一次，别每一帧都落盘。
    val lastPage = remember { mutableIntStateOf(startPage) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { lastPage.intValue = it }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (!scrolling && !resuming) {
                    onProgress(order, lastPage.intValue, pager.total ?: pager.items.size)
                }
            }
    }
    // 退出阅读器时也记一笔，免得刚划两下就退出去没存上。
    DisposableEffect(Unit) {
        onDispose {
            if (!resuming) onProgress(order, lastPage.intValue, pager.total ?: pager.items.size)
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
    // 总页数：服务端给了就用它，还没拉到就用已经有的条数。
    val pageCount = pager.total.takeIf { it > 0 } ?: pager.items.size

    Box(modifier.fillMaxSize().background(Color.Black)) {
            // 放大后的拖动位移：平移画面，顶到边上就转成滚列表。
            fun applyPan(delta: Offset) {
                val s = scale
                if (s <= 1f || viewport == IntSize.Zero) return
                // 手势监听在缩放图层外面，delta 就是屏幕像素，不用再乘缩放。
                val desired = offset + delta
                val clamped = clampOffset(desired, s, viewport)
                offset = clamped
                // 顶到边上还继续拖：剩下的屏幕距离拿去滚列表，接着往下看。
                val leftover = desired.y - clamped.y
                // 同步派发：走 scroll() 会在列表到头时触发系统 overscroll 弹簧，那才是"回弹"。
                if (leftover != 0f) listState.dispatchRawDelta(-leftover / s)
            }

            // 内容本身不拦点击，点一下切换工具条；双指放大时整块画面一起缩放。
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { viewport = it }
                    // 手势监听挂在缩放图层外面：坐标不跟着缩放变，手指和画面才一一对应，
                    // 挂在里面的话 Compose 拿缩放后的坐标算位移，会越拖越跟不上、松手还往回弹。
                    .pointerInput(Unit) {
                        detectReaderGestures(
                            zoomed = { scale > 1f },
                            onStart = {
                                flingJob?.cancel()
                                zoomAccum = 1f
                            },
                            onZoom = { zoom, pan, centroid ->
                                // 两指斜着划时指间距会抖，把抖动累计起来，超过 3% 才算真的在捏。
                                zoomAccum *= zoom
                                val steady = if (abs(zoomAccum - 1f) < 0.03f) {
                                    1f
                                } else {
                                    zoomAccum.also { zoomAccum = 1f }
                                }
                                val next = (scale * steady).coerceIn(1f, MAX_ZOOM)
                                val center = Offset(viewport.width / 2f, viewport.height / 2f)
                                // 手指按住的那一点，缩放前后停在原处；坐标都是屏幕坐标。
                                val k = 1f - next / scale
                                val moved = offset + pan + (centroid - center - offset) * k
                                scale = next
                                offset = if (next <= 1f) {
                                    Offset.Zero
                                } else {
                                    clampOffset(moved, next, viewport)
                                }
                            },
                            onDrag = { drag -> applyPan(drag) },
                            onFling = { velocity ->
                                if (scale > 1f && velocity.getDistance() > 120f) {
                                    flingJob?.cancel()
                                    // 松手后按速度继续滑，滑到边上就顺着滚列表。
                                    flingJob = scope.launch {
                                        var v = velocity
                                        var last = withFrameNanos { it }
                                        while (v.getDistance() > 40f) {
                                            withFrameNanos { now ->
                                                val dt = ((now - last) / 1_000_000_000f)
                                                    .coerceIn(0f, 0.05f)
                                                last = now
                                                applyPan(v * dt)
                                                v *= exp(-3f * dt)
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { chrome = !chrome })
                    },
            ) {
                // 阅读器里不要系统的边缘拉伸回弹：滑到头就停住，弹回来反而像卡了一下。
                CompositionLocalProvider(LocalOverscrollFactory provides null) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                    ) {
                        // 放大时把列表自己的滚动关掉：不然它和我们的平移抢手势，边角上会弹一下。
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = !zoomed,
                        ) {
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
                            text = title.ifBlank { "第 $order 话" },
                            style = MiuixTheme.textStyles.subtitle,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = comicTitle,
                            style = MiuixTheme.textStyles.footnote2,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = if (totalPages > 0) "$shown / $totalPages" else "$shown",
                        style = MiuixTheme.textStyles.footnote1,
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 定位条：拖到哪一页松手就跳到哪一页。总页数还没出来时不显示，免得条是空的。
                    if (pageCount > 1) {
                        ReaderSeekBar(
                            current = (shown - 1).coerceIn(0, pageCount - 1),
                            total = pageCount,
                            onJump = { target ->
                                scope.launch {
                                    // 跳到看得清的位置：先退出放大，不然画面还偏在别处。
                                    flingJob?.cancel()
                                    scale = 1f
                                    offset = Offset.Zero
                                    jumpToPage(pager, listState, target)
                                }
                            },
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EpisodeButton(
                            label = "上一话",
                            enabled = prevOrder != null,
                            modifier = Modifier.weight(1f),
                        ) {
                            prevOrder?.let { order = it }
                        }
                        EpisodeButton(
                            label = "下一话",
                            enabled = nextOrder != null,
                            modifier = Modifier.weight(1f),
                        ) {
                            nextOrder?.let { order = it }
                        }
                    }
                }
        }
    }
}

/**
 * 跳到第 [target] 页（0 基）。
 *
 * 定位条可以直接指到还没下载到的地方，所以先把中间那几页拉完再滚过去；
 * 拉不动（没更多了或者报错）就停在能到的最远那一页。
 */
private suspend fun jumpToPage(
    pager: Pager<*>,
    listState: LazyListState,
    target: Int,
) {
    if (target < 0) return
    var guard = 0
    while (pager.items.size <= target && pager.hasMore && pager.error == null && guard < 80) {
        pager.loadNext()
        guard++
    }
    if (pager.items.isEmpty()) return
    listState.scrollToItem(target.coerceAtMost(pager.items.size - 1))
}

/**
 * 阅读器底部的定位条：左边当前页，右边总页数，中间是一条细轨道。
 *
 * 拖动过程中只更新页码预览，松手才真跳（边拖边滚会把图片请求打爆，也跟手不跟手）。
 */
@Composable
private fun ReaderSeekBar(
    current: Int,
    total: Int,
    onJump: (Int) -> Unit,
) {
    var preview by remember { mutableStateOf<Int?>(null) }
    val index = (preview ?: current).coerceIn(0, (total - 1).coerceAtLeast(0))
    val fraction = if (total <= 1) 0f else index.toFloat() / (total - 1)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "${index + 1}",
            style = MiuixTheme.textStyles.footnote1,
            color = Color.White.copy(alpha = 0.9f),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(26.dp)
                .pointerInput(total) {
                    if (total <= 1) return@pointerInput
                    fun indexAt(x: Float): Int {
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val f = (x / w).coerceIn(0f, 1f)
                        return (f * (total - 1)).roundToInt().coerceIn(0, total - 1)
                    }
                    detectHorizontalDragGestures(
                        onDragStart = { at -> preview = indexAt(at.x) },
                        onHorizontalDrag = { change, _ -> preview = indexAt(change.position.x) },
                        onDragEnd = {
                            preview?.let(onJump)
                            preview = null
                        },
                        onDragCancel = { preview = null },
                    )
                }
                .pointerInput(total) {
                    if (total <= 1) return@pointerInput
                    detectTapGestures { at ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val f = (at.x / w).coerceIn(0f, 1f)
                        onJump((f * (total - 1)).roundToInt().coerceIn(0, total - 1))
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val centerY = size.height / 2f
                val trackWidth = 3.dp.toPx()
                val radius = 7.dp.toPx()
                val start = radius
                val usable = (size.width - radius * 2).coerceAtLeast(1f)
                val x = start + usable * fraction
                drawLine(
                    color = Color.White.copy(alpha = 0.22f),
                    start = Offset(start, centerY),
                    end = Offset(start + usable, centerY),
                    strokeWidth = trackWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.85f),
                    start = Offset(start, centerY),
                    end = Offset(x, centerY),
                    strokeWidth = trackWidth,
                    cap = StrokeCap.Round,
                )
                drawCircle(color = Color.White, radius = radius, center = Offset(x, centerY))
            }
        }
        Text(
            text = "/ $total",
            style = MiuixTheme.textStyles.footnote1,
            color = Color.White.copy(alpha = 0.6f),
        )
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
                            style = MiuixTheme.textStyles.body1,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                        PicaSurface(
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
                                style = MiuixTheme.textStyles.button,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                } else {
                    PicaCircularProgress(
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

/** 底部翻话按钮：能翻就白底字，不能翻就灰掉。 */
@Composable
private fun EpisodeButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    PicaSurface(
        shape = RoundedCornerShape(18.dp),
        color = if (enabled) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f),
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.button,
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
                    style = MiuixTheme.textStyles.body1,
                    color = Color.White.copy(alpha = 0.8f),
                )
                PicaSurface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.16f),
                    modifier = Modifier.clickable(onClick = onRetry),
                ) {
                    Text(
                        text = "再试一次",
                        style = MiuixTheme.textStyles.button,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
            }

            loading -> Text(
                text = "正在拉图片…",
                style = MiuixTheme.textStyles.body1,
                color = Color.White.copy(alpha = 0.8f),
            )

            else -> Text(
                text = "这话没有图片",
                style = MiuixTheme.textStyles.body1,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

/** 缩放 / 平移时把画面限制在看得见内容的范围内，别拖出去只剩黑边。 */
private fun clampOffset(desired: Offset, scale: Float, viewport: IntSize): Offset {
    val maxX = (scale - 1f) * viewport.width / 2f
    val maxY = (scale - 1f) * viewport.height / 2f
    return Offset(desired.x.coerceIn(-maxX, maxX), desired.y.coerceIn(-maxY, maxY))
}

/**
 * 阅读器的手势：
 * - 两根手指：捏合缩放 + 拖动平移；
 * - 放大状态下单指：拖动平移（拖到边上再继续拖就滚列表，接着往下看）；
 * - 没放大时单指：照旧交给列表滚动，单击照旧切换工具条。
 *
 * 事件都在 Initial 阶段抢，并且只在真正开始缩放/拖动之后才消费，
 * 这样单击切工具栏不会被误吞。
 */
private suspend fun PointerInputScope.detectReaderGestures(
    zoomed: () -> Boolean,
    onStart: () -> Unit,
    onZoom: (zoom: Float, pan: Offset, centroid: Offset) -> Unit,
    onDrag: (drag: Offset) -> Unit,
    onFling: (velocity: Offset) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onStart()
        val tracker = VelocityTracker()
        var pinching = false
        var dragging = false
        var moved = Offset.Zero
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) {
                // 松手：把最后那段速度交出去，接着滑。
                if (dragging) {
                    val v = tracker.calculateVelocity()
                    onFling(Offset(v.x, v.y))
                }
                return@awaitEachGesture
            }

            if (pressed.size >= 2) {
                pinching = true
                dragging = false
                onZoom(event.calculateZoom(), event.calculatePan(), event.calculateCentroid())
                event.changes.forEach { it.consume() }
                continue
            }
            // 缩放收尾时剩下的那根手指别再触发滚动或平移，松手前先按住。
            if (pinching) {
                event.changes.forEach { it.consume() }
                continue
            }
            if (!zoomed()) continue

            val change = pressed.first()
            val delta = change.positionChange()
            if (!dragging) {
                moved += delta
                if (moved.getDistance() > viewConfiguration.touchSlop) {
                    dragging = true
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    // 起手那几像素之前没消费，这里一次补上，画面才不会跳一下。
                    onDrag(moved)
                    moved = Offset.Zero
                }
            } else {
                change.consume()
                tracker.addPosition(change.uptimeMillis, change.position)
                onDrag(delta)
            }
        }
    }
}
