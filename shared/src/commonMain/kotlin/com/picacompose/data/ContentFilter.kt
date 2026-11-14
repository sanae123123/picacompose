package com.picacompose.data

import androidx.compose.runtime.mutableIntStateOf
import com.picacompose.picaapi.model.ComicSummary

/**
 * 内容过滤：只看标了「无 H」的作品。
 *
 * 开关存在本机，默认关。判断依据是列表项自带的标签和简介：
 *
 * - 标签是主依据：PicACG 上非 H 的作品一般会打 `無H內容`（繁体）或 `无H内容` 这类标签，
 *   详情页显示成 `#無H內容`，接口里不带 `#`。
 * - 简介是补充：实测只有 `POST comics/advanced-search` 返回简介，`GET comics`、排行榜、
 *   收藏的列表项都没有这个字段。所以搜索页能按简介筛，其它页面只能看标签。
 * - 两边都按「不分大小写、无 / 無 都算」匹配一个 `H`。
 *
 * 过滤放在列表这一层（Pager 的 filter 或者拿回来之后 filter）：服务端没有「只要无 H」
 * 这种查询参数，只能本地筛。
 */
object ContentFilter {

    private val kv by lazy { platformKeyValueStore() }

    /** 开关变动时加一；列表页读它当 remember 的 key，切完开关能把旧列表重拉一遍。 */
    private val versionState = mutableIntStateOf(0)

    /** 只显示无 H 的作品。默认关。 */
    var enabled: Boolean
        get() = kv.get(KEY_ENABLED) == "1"
        set(value) {
            kv.put(KEY_ENABLED, if (value) "1" else "0")
            versionState.intValue++
        }

    /** 开关版本号，只读；Compose 读它会订阅开关变化。 */
    val version: Int get() = versionState.intValue

    /** 第一次登录的那个询问弹过没有，弹过就不再弹。 */
    var prompted: Boolean
        get() = kv.get(KEY_PROMPTED) == "1"
        set(value) {
            kv.put(KEY_PROMPTED, if (value) "1" else "0")
        }

    /** 这一本能不能出现在列表里：开关关着全放行，开着就要求带无 H 标记。 */
    fun allows(comic: ComicSummary): Boolean = !enabled || comic.isMarkedNoH()

    private const val KEY_ENABLED = "filter.noHOnly"
    private const val KEY_PROMPTED = "filter.noHPrompted"
}

/** 标签或简介里带了「无 H / 無 H」。 */
fun ComicSummary.isMarkedNoH(): Boolean =
    hasNoHMark(description) || tags.any { tag -> hasNoHMark(tag) }

/**
 * 判断一段文字里有没有「无 H」的意思。
 *
 * 只认 `无h` / `無h` 这种连着的写法（`無H內容` 正好命中）；`H` 单独出现太容易误伤
 * （作品名、作者名里都有），不认。
 */
private fun hasNoHMark(text: String?): Boolean {
    if (text.isNullOrBlank()) return false
    val lower = text.lowercase()
    return lower.contains("无h") || lower.contains("無h")
}
