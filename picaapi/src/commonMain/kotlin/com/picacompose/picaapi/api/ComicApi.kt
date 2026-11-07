package com.picacompose.picaapi.api

import com.picacompose.picaapi.PicaClient
import com.picacompose.picaapi.model.AdvancedSearchRequest
import com.picacompose.picaapi.model.CollectionGroup
import com.picacompose.picaapi.model.CollectionsData
import com.picacompose.picaapi.model.ComicDetail
import com.picacompose.picaapi.model.ComicDetailData
import com.picacompose.picaapi.model.ComicListData
import com.picacompose.picaapi.model.ComicPagesData
import com.picacompose.picaapi.model.ComicRandomData
import com.picacompose.picaapi.model.ComicSummary
import com.picacompose.picaapi.model.Episode
import com.picacompose.picaapi.model.EpisodeListData
import com.picacompose.picaapi.model.KnightLeaderboardData
import com.picacompose.picaapi.model.Paged
import com.picacompose.picaapi.model.UserBasic
import io.ktor.http.HttpMethod

/** 漫画相关接口。 */
class ComicApi internal constructor(private val client: PicaClient) {

    /**
     * `GET comics`，漫画列表 / 分类检索。
     *
     * @param category 分类名（来自 `meta.categories()` 或详情里的 categories）
     * @param sort 排序：`dd` 最新、`da` 最早、`ld` 最多爱心、`vd` 最多浏览
     */
    suspend fun list(
        page: Int = 1,
        category: String? = null,
        tag: String? = null,
        author: String? = null,
        finished: Boolean? = null,
        sort: String? = null,
        categoryType: String? = null,
        categoryArea: String? = null,
    ): Paged<ComicSummary> = client.call(
        method = HttpMethod.Get,
        path = "comics",
        query = mapOf(
            "page" to page.toString(),
            "c" to category,
            "t" to tag,
            "a" to author,
            "f" to finished?.toString(),
            "s" to sort,
            "ct" to categoryType,
            "ca" to categoryArea,
        ),
        serializer = ComicListData.serializer(),
    ).comics

    /**
     * `POST comics/advanced-search`，高级搜索（关键词 + 分类 + 排序）。
     *
     * 注意分页是 query 参数，筛选条件在 body 里，和普通搜索正好反过来。
     */
    suspend fun advancedSearch(
        keyword: String,
        categories: List<String> = emptyList(),
        sort: String? = null,
        page: Int = 1,
    ): Paged<ComicSummary> {
        val body = client.json.encodeToString(
            AdvancedSearchRequest.serializer(),
            AdvancedSearchRequest(keyword = keyword, sort = sort, categories = categories),
        )
        return client.call(
            method = HttpMethod.Post,
            path = "comics/advanced-search",
            query = mapOf("page" to page.toString()),
            bodyJson = body,
            serializer = ComicListData.serializer(),
        ).comics
    }

    /** `GET collections`，首页的合集分组。 */
    suspend fun collections(): List<CollectionGroup> = client.call(
        method = HttpMethod.Get,
        path = "collections",
        serializer = CollectionsData.serializer(),
    ).collections

    /** `GET comics/knight-leaderboard`，骑士榜（上传/翻译贡献榜）。 */
    suspend fun knightLeaderboard(): List<UserBasic> = client.call(
        method = HttpMethod.Get,
        path = "comics/knight-leaderboard",
        serializer = KnightLeaderboardData.serializer(),
    ).users

    /** `GET comics/random`，随机推荐。 */
    suspend fun random(): List<ComicSummary> = client.call(
        method = HttpMethod.Get,
        path = "comics/random",
        serializer = ComicRandomData.serializer(),
    ).comics

    /** `GET comics/leaderboard`，排行榜。tt：H24 / D7 / D30。 */
    suspend fun leaderboard(timeType: String = "H24", category: String = "VC"): List<ComicSummary> =
        client.call(
            method = HttpMethod.Get,
            path = "comics/leaderboard",
            query = mapOf("tt" to timeType, "ct" to category),
            serializer = ComicRandomData.serializer(),
        ).comics

    /** `GET comics/{comicId}`，详情。 */
    suspend fun detail(comicId: String): ComicDetail = client.call(
        method = HttpMethod.Get,
        path = "comics/$comicId",
        serializer = ComicDetailData.serializer(),
    ).comic

    /** `GET comics/{comicId}/eps`，章节列表。 */
    suspend fun episodes(comicId: String, page: Int = 1): Paged<Episode> = client.call(
        method = HttpMethod.Get,
        path = "comics/$comicId/eps",
        query = mapOf("page" to page.toString()),
        serializer = EpisodeListData.serializer(),
    ).eps

    /** `GET comics/{comicId}/order/{order}/pages`，按章节序号取图片列表。 */
    suspend fun pages(comicId: String, order: Int, page: Int = 1): ComicPagesData = client.call(
        method = HttpMethod.Get,
        path = "comics/$comicId/order/$order/pages",
        query = mapOf("page" to page.toString()),
        serializer = ComicPagesData.serializer(),
    )

    /** `GET comics/{comicId}/recommendation`，相关推荐。 */
    suspend fun recommendation(comicId: String): List<ComicSummary> = client.call(
        method = HttpMethod.Get,
        path = "comics/$comicId/recommendation",
        serializer = ComicRandomData.serializer(),
    ).comics

    /** `POST comics/{comicId}/like`，点赞 / 取消点赞（返回 action）。 */
    suspend fun like(comicId: String): String? = client.call(
        method = HttpMethod.Post,
        path = "comics/$comicId/like",
        serializer = com.picacompose.picaapi.model.ActionData.serializer(),
    ).action

    /** `POST comics/{comicId}/favourite`，收藏 / 取消收藏。 */
    suspend fun favourite(comicId: String): String? = client.call(
        method = HttpMethod.Post,
        path = "comics/$comicId/favourite",
        serializer = com.picacompose.picaapi.model.ActionData.serializer(),
    ).action

    /** `GET users/favourite`，我的收藏。 */
    suspend fun favourites(page: Int = 1, sort: String? = null): Paged<ComicSummary> = client.call(
        method = HttpMethod.Get,
        path = "users/favourite",
        query = mapOf("page" to page.toString(), "s" to sort),
        serializer = ComicListData.serializer(),
    ).comics
}
