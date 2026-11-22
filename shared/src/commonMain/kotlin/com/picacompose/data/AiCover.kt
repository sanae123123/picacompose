package com.picacompose.data

import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.delay

/**
 * 分类磁贴的封面：开了「只看无 H」以后，包里那张官方抠图就不放了（有的挺露），
 * 改成让本机画图引擎按磁贴名现画一张同类题材的 —— 图上还是「排行榜」「留言板」那点意思，
 * 但不是原来那张。
 *
 * 图和帮助页一样由 [LocalHttpServer] 在本机端口上喂出去，磁贴只管请求
 * `http://127.0.0.1:端口/aicover/<art>`。这样图片加载和接口客户端都不用动。
 *
 * 请求只发已经画好的图（没画好就 404，磁贴先空着），画图在 [prefetch] 里后台一张张来：
 * 画一张、版本号加一，磁贴换了地址自然就把新图拉下来了。别让 http 请求卡着等出图，
 * 七块磁贴同时进来会把引擎排成十秒的队，客户端早超时了。
 *
 * 引擎不在（桌面版，或者手机上那个会发图的 App 没开）就一张都画不出来，磁贴空着，
 * 但不会把官方那张露的图放出来。
 */
object AiCover {

    private val versionState = mutableIntStateOf(0)

    /** 又画好一张：磁贴读它换地址重新拉。 */
    val version: Int get() = versionState.intValue

    /** 首页那几块磁贴，按这个顺序后台预生成。 */
    val TILES = listOf("support", "leaderboard", "game", "love_pica", "forum", "latest", "random", "splash")

    /** 已经画好的封面字节；没画过返回 null，不会现画。 */
    suspend fun cached(art: String): ByteArray? = aiCoverCached(art)

    /** 画一张并缓存，画好就通知磁贴换图。 */
    suspend fun draw(art: String): ByteArray? {
        val bytes = aiCoverDraw(art, promptFor(art), NEGATIVE, seedOf(art)) ?: return null
        versionState.intValue++
        return bytes
    }

    /**
     * 空闲时把磁贴封面一张张画出来。
     * 引擎连续画会偶发失败，失败就歇一下重来一次；两次都不行就先跳过，下次进页面再说。
     */
    suspend fun prefetch() {
        for (art in TILES) {
            if (aiCoverCached(art) != null) continue
            var bytes = aiCoverDraw(art, promptFor(art), NEGATIVE, seedOf(art))
            if (bytes == null) {
                delay(500)
                bytes = aiCoverDraw(art, promptFor(art), NEGATIVE, seedOf(art))
            }
            if (bytes != null) versionState.intValue++
        }
    }

    /** 磁贴名 → 一句安全场景。认不出来的按 hash 挑通用场景。 */
    fun promptFor(art: String): String = "$PREFIX, ${SCENES[art] ?: fallbackFor(art)}"

    private fun fallbackFor(art: String): String = FALLBACK[seedOf(art) % FALLBACK.size]

    /** 稳定的小种子：同一块磁贴每次画出来都差不多。 */
    internal fun seedOf(art: String): Int {
        var h = 0
        for (c in art) h = h * 31 + c.code
        return (h and 0x7fffffff) % 1_000_000
    }

    private const val PREFIX =
        "anime illustration, atmospheric background art, soft light, clean, detailed, no people"

    /** 安全线：这些词一个都别出现。 */
    internal const val NEGATIVE =
        "nsfw, nude, naked, topless, bottomless, explicit, sexual, erotic, hentai, " +
            "cleavage, lingerie, underwear, bikini, suggestive, person, girl, woman, " +
            "text, watermark, signature, logo, lowres, blurry, worst quality, deformed"

    /** 磁贴名 → 场景，尽量让人一眼看出这块是干什么的。 */
    private val SCENES = mapOf(
        "support" to "a gift box tied with a red ribbon on a wooden table, morning light",
        "leaderboard" to "a gold trophy on a podium under a starry night sky",
        "game" to "retro arcade machines glowing in a dim room, evening",
        "love_pica" to "a smartphone lying on a desk, soft glowing screen, cozy room",
        "forum" to "a wall covered with colorful sticky notes, warm afternoon light",
        "latest" to "a stack of fresh books beside a window, morning light",
        "random" to "two dice and playing cards on a table, dramatic light",
        "splash" to "a small glowing lantern on a windowsill at dusk, warm light",
    )

    private val FALLBACK = listOf(
        "anime style landscape, rolling green hills at golden hour",
        "quiet seaside town, pastel sky, calm water",
        "forest path with sunbeams breaking through leaves",
        "night sky full of stars over a still lake",
        "rooftop view of a small city at sunset",
        "wide flower field under a clear blue sky",
        "rainy window, warm interior light, soft bokeh",
    )
}

/** 已经画好的封面字节；没画过返回 null。 */
internal expect suspend fun aiCoverCached(art: String): ByteArray?

/** 现画一张并存到缓存里。Android 上调本机引擎；其它平台没有这引擎，返回 null。 */
internal expect suspend fun aiCoverDraw(
    art: String,
    prompt: String,
    negative: String,
    seed: Int,
): ByteArray?
