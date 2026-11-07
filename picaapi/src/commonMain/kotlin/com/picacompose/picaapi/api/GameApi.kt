package com.picacompose.picaapi.api

import com.picacompose.picaapi.PicaClient
import com.picacompose.picaapi.model.ActionData
import com.picacompose.picaapi.model.CommentListData
import com.picacompose.picaapi.model.CommentRequest
import com.picacompose.picaapi.model.GameDetail
import com.picacompose.picaapi.model.GameDetailData
import com.picacompose.picaapi.model.GameListData
import com.picacompose.picaapi.model.GameSummary
import com.picacompose.picaapi.model.Paged
import io.ktor.http.HttpMethod

/** 游戏（官方 App 的 apps 区）接口。 */
class GameApi internal constructor(private val client: PicaClient) {

    /** `GET games`，游戏列表。 */
    suspend fun list(page: Int = 1): Paged<GameSummary> = client.call(
        method = HttpMethod.Get,
        path = "games",
        query = mapOf("page" to page.toString()),
        serializer = GameListData.serializer(),
    ).games

    /** `GET games/{gameId}`，游戏详情。 */
    suspend fun detail(gameId: String): GameDetail = client.call(
        method = HttpMethod.Get,
        path = "games/$gameId",
        serializer = GameDetailData.serializer(),
    ).game

    /** `POST games/{gameId}/like`，点赞 / 取消。 */
    suspend fun like(gameId: String): String? = client.call(
        method = HttpMethod.Post,
        path = "games/$gameId/like",
        serializer = ActionData.serializer(),
    ).action

    /** `GET games/{gameId}/comments`，游戏评论区。 */
    suspend fun comments(gameId: String, page: Int = 1): CommentListData = client.call(
        method = HttpMethod.Get,
        path = "games/$gameId/comments",
        query = mapOf("page" to page.toString()),
        serializer = CommentListData.serializer(),
    )

    /** `POST games/{gameId}/comments`，给游戏发评论。 */
    suspend fun postComment(gameId: String, content: String) {
        val body = client.json.encodeToString(CommentRequest.serializer(), CommentRequest(content))
        client.callWithoutResult(
            method = HttpMethod.Post,
            path = "games/$gameId/comments",
            bodyJson = body,
        )
    }
}
