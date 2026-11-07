package com.picacompose.picaapi

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 真实接口测试。
 *
 * 需要环境变量 `PICA_EMAIL` / `PICA_PASSWORD`，没有就自动跳过（不影响日常 CI）。
 * `.github/workflows/live-api.yml` 会从仓库 Secret 注入这两个变量后手动触发。
 */
class LiveApiTest {

    private val email: String? = System.getenv("PICA_EMAIL")
    private val password: String? = System.getenv("PICA_PASSWORD")

    @Test
    fun liveEndpoints() = runBlocking {
        if (email.isNullOrBlank() || password.isNullOrBlank()) {
            println("skip LiveApiTest: PICA_EMAIL / PICA_PASSWORD 未设置")
            return@runBlocking
        }

        val client = PicaClient()
        try {
            // 登录
            val signIn = client.auth.signIn(email, password)
            assertTrue(signIn.token.isNotBlank(), "sign-in 应该返回 token")
            println("sign-in: token=${signIn.token.take(8)}…")

            // init / 分类 / 轮播 / 热搜
            val init = client.meta.init()
            assertTrue(init.categories.isNotEmpty(), "init.categories 不应为空")
            println("init: imageServer=${init.imageServer} categories=${init.categories.size} isPunched=${init.isPunched}")
            val categories = client.meta.categories()
            val banners = client.meta.banners()
            val keywords = client.meta.keywords()
            assertTrue(categories.isNotEmpty(), "categories 不应为空")
            assertTrue(banners.isNotEmpty(), "banners 不应为空")
            println("categories=${categories.size} banners=${banners.size} keywords=${keywords.size}")

            // 漫画列表 / 检索 / 排序
            val list = client.comics.list(page = 1)
            assertTrue(list.docs.isNotEmpty(), "漫画列表不应为空")
            val first = list.docs.first()
            println("comics: total=${list.total} first=${first.title}(${first.id})")
            val filtered = client.comics.list(page = 1, category = "全彩")
            assertTrue(filtered.docs.isNotEmpty(), "按分类检索不应为空")
            println("comics?c=全彩: total=${filtered.total}")

            // 随机 / 排行榜
            val random = client.comics.random()
            assertTrue(random.isNotEmpty(), "随机推荐不应为空")
            val board = client.comics.leaderboard()
            assertTrue(board.isNotEmpty(), "排行榜不应为空")
            println("random=${random.size} leaderboard=${board.size}")

            // 详情 / 章节 / 图片
            val detail = client.comics.detail(first.id)
            assertTrue(detail.title.isNotBlank(), "详情标题不应为空")
            println("detail: ${detail.title} eps=${detail.episodesCount} pages=${detail.pagesCount}")

            val eps = client.comics.episodes(first.id)
            assertTrue(eps.docs.isNotEmpty(), "章节列表不应为空")
            val episode = eps.docs.first()
            println("eps: total=${eps.total} first order=${episode.order}")

            val pages = client.comics.pages(first.id, episode.order)
            assertTrue(pages.pages.docs.isNotEmpty(), "图片列表不应为空")
            val imageUrl = client.imageUrl(pages.pages.docs.first().media)
            assertTrue(!imageUrl.isNullOrBlank(), "图片地址不应为空")
            println("pages: total=${pages.pages.total} image=${imageUrl}")

            val epPages = client.episodes.pages(episode.id)
            assertTrue(epPages.pages.docs.isNotEmpty(), "按章节 id 取图不应为空")
            println("eps/{id}/pages: total=${epPages.pages.total}")

            val recommendation = client.comics.recommendation(first.id)
            println("recommendation=${recommendation.size}")

            // 评论
            val comments = client.comments.list(first.id)
            println("comments: total=${comments.comments.total} top=${comments.topComments.size}")
            comments.comments.docs.firstOrNull()?.let { comment ->
                val children = client.comments.children(comment.id)
                println("childrens: total=${children.total}")
            }

            // 用户
            val profile = client.users.profile()
            assertTrue(profile.name.isNotBlank(), "个人资料 name 不应为空")
            println("profile: ${profile.name} level=${profile.level} exp=${profile.exp}")
            val otherProfile = client.users.profileById(profile.id)
            println("profileById: ${otherProfile.name}")
            val favourites = client.comics.favourites()
            println("favourites: total=${favourites.total}")

            // 合集 / 骑士榜（新增）
            val collections = client.comics.collections()
            assertTrue(collections.isNotEmpty(), "合集不应为空")
            println("collections: groups=${collections.size} 首个=${collections.first().comics.size} 本")

            val knights = client.comics.knightLeaderboard()
            println("knight-leaderboard: ${knights.size} 人，首位=${knights.firstOrNull()?.name}")

            // 高级搜索（POST，但是只读）
            val advanced = client.comics.advancedSearch(keyword = "丝袜", page = 1)
            println("advanced-search: total=${advanced.total} 本页=${advanced.docs.size}")

            // 我的评论 / 通知（新增）
            val myComments = client.comments.myComments()
            println("my-comments: total=${myComments.total}")
            myComments.docs.firstOrNull()?.let { println("  第一条: ${it.comic?.title} / ${it.content.take(20)}") }

            val notifications = client.users.notifications()
            println("notifications: total=${notifications.total} 本页=${notifications.docs.size}")
            notifications.docs.firstOrNull()?.let { println("  第一条: ${it.title}") }

            // 错误信封
            val error = assertFailsWith<PicaException> {
                client.comics.detail("000000000000000000000000")
            }
            println("error: httpCode=${error.httpCode} errorCode=${error.errorCode} message=${error.message}")

            println("LIVE TEST ALL GREEN")
        } finally {
            client.close()
        }
    }
}
