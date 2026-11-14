package com.picacompose.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.picacompose.ui.skin.SkinRegistry
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 一排单选胶囊，选中时那块蓝色会从上一个滑到新的那个。
 *
 * 做法：这排胶囊自己不画底色，只在下面垫一块同色块，位置和宽度都按当前选中项算。
 * 每个胶囊布局完报到根节点的矩形（[onGloballyPositioned]），减去这一排自己的原点就是
 * 胶囊在容器里的位置；色块用 [Animatable] 追过去，换宽度时连宽一起变，看起来像一整块
 * 蓝在排里滑，而不是两块蓝色淡入淡出。
 *
 * 只适合「一排、单选」的组。多选（标签筛选）或者会换行的（排序方式那种 FlowRow）
 * 还是走 [PicaFilterChip] 的淡入淡出，因为一个色块没法同时盖住两行里的两个选项。
 *
 * @param options 每个胶囊上的文字，顺序就是显示顺序。
 * @param selectedIndex 当前选中项，越界按 none 处理（色块保持不动）。
 * @param onSelect 点某个胶囊时回调它的下标。
 * @param textStyle 胶囊文字样式，各页面字阶不一样所以让调用方传。
 * @param spacing 胶囊之间的间距。
 */
@Composable
fun PicaSlidingChipRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MiuixTheme.textStyles.body1,
    spacing: Dp = 10.dp,
) {
    val colors = MiuixTheme.colorScheme
    val chipRadius = SkinRegistry.current.tokens.chipRadius
    val density = LocalDensity.current

    // 每个胶囊在根节点里的矩形；先塞占位值，布局完再填真的。
    val chipBounds = remember(options.size) {
        mutableStateListOf<Rect>().apply { repeat(options.size) { add(Rect.Zero) } }
    }
    var rowBounds by remember { mutableStateOf(Rect.Zero) }

    // 选中项在容器里的位置：量到之后才有值，没量到先不画。
    val target = if (selectedIndex in chipBounds.indices && rowBounds != Rect.Zero) {
        val rect = chipBounds[selectedIndex]
        if (rect == Rect.Zero) {
            null
        } else {
            Rect(
                left = rect.left - rowBounds.left,
                top = rect.top - rowBounds.top,
                right = rect.right - rowBounds.left,
                bottom = rect.bottom - rowBounds.top,
            )
        }
    } else {
        null
    }

    val left = remember { Animatable(0f) }
    val top = remember { Animatable(0f) }
    val width = remember { Animatable(0f) }
    val height = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }

    // 蓝块此刻的矩形；跟着动画走，用来判断哪个胶囊正被盖住，字才好变白。
    val indicatorRect = Rect(left.value, top.value, left.value + width.value, top.value + height.value)

    LaunchedEffect(target) {
        val rect = target ?: return@LaunchedEffect
        // 第一次布局直接落位，别从左上角飞出来。
        if (!placed) {
            left.snapTo(rect.left)
            top.snapTo(rect.top)
            width.snapTo(rect.width)
            height.snapTo(rect.height)
            placed = true
        } else {
            // 位置和宽高一起动，否则宽度先变完位置再追，会看起来顿一下。
            val spec = tween<Float>(durationMillis = 240, easing = FastOutSlowInEasing)
            coroutineScope {
                launch { left.animateTo(rect.left, spec) }
                launch { top.animateTo(rect.top, spec) }
                launch { width.animateTo(rect.width, spec) }
                launch { height.animateTo(rect.height, spec) }
            }
        }
    }

    Box {
        // 会滑的那块蓝。
        Box(
            modifier = Modifier
                .offset { IntOffset(left.value.roundToInt(), top.value.roundToInt()) }
                .size(
                    width = with(density) { width.value.toDp() },
                    height = with(density) { height.value.toDp() },
                )
                .clip(RoundedCornerShape(chipRadius))
                .background(colors.primary),
        )
        Row(
            modifier = modifier.onGloballyPositioned { rowBounds = it.boundsInRoot() },
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            options.forEachIndexed { index, label ->
                val chipRect = chipBounds[index]
                // 蓝块滑到脚下时字才变白；没量到之前按选中项算，免得第一帧闪一下。
                val covered = if (!placed) {
                    index == selectedIndex
                } else {
                    chipRect != Rect.Zero &&
                        coverage(
                            chip = chipRect.translate(-rowBounds.left, -rowBounds.top),
                            indicator = indicatorRect,
                        ) >= 0.5f
                }
                val textColor by animateColorAsState(
                    targetValue = if (covered) colors.onPrimary else colors.onSurface,
                    animationSpec = tween(durationMillis = 140),
                    label = "chipText$index",
                )
                // 没被蓝块盖住的胶囊留一圈描边，不然选中项一跑，别的胶囊就只剩字悬在那。
                val borderColor by animateColorAsState(
                    targetValue = if (covered) Color.Transparent else colors.dividerLine,
                    animationSpec = tween(durationMillis = 180),
                    label = "chipBorder$index",
                )
                Box(
                    modifier = Modifier
                        // 布局完了把位置报上去；值没变就不写，免得每帧触发重组。
                        .onGloballyPositioned { coordinates ->
                            val rect = coordinates.boundsInRoot()
                            if (rect != chipBounds[index]) chipBounds[index] = rect
                        }
                        .clip(RoundedCornerShape(chipRadius))
                        .border(1.dp, borderColor, RoundedCornerShape(chipRadius))
                        .clickable { onSelect(index) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = label, style = textStyle, color = textColor)
                }
            }
        }
    }
}

/** 指示块盖住某个胶囊的比例，0..1。 */
private fun coverage(chip: Rect, indicator: Rect): Float {
    val width = minOf(chip.right, indicator.right) - maxOf(chip.left, indicator.left)
    val height = minOf(chip.bottom, indicator.bottom) - maxOf(chip.top, indicator.top)
    if (width <= 0f || height <= 0f) return 0f
    val area = chip.width * chip.height
    if (area <= 0f) return 0f
    return (width * height) / area
}
