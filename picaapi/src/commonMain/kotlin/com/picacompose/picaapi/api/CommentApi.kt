package com.picacompose.picaapi.api

import com.picacompose.picaapi.PicaClient
import com.picacompose.picaapi.model.ActionData
import com.picacompose.picaapi.model.Comment
import com.picacompose.picaapi.model.CommentListData
import com.picacompose.picaapi.model.CommentRequest
import com.picacompose.picaapi.model.MessageData
import com.picacompose.picaapi.model.TopCommentData
import com.picacompose.picaapi.model.MyComment
import com.picacompose.picaapi.model.MyCommentsData
import com.picacompose.picaapi.model.Paged
import io.ktor.http.HttpMethod

/** 评论相关接口。 */
class CommentApi internal constructor(private val client: PicaClient) {

    /** `GET comics/{comicId}/comments`，漫画评论区（含置顶）。 */
    suspend fun list(comicId: String, page: Int = 1): CommentListData = client.call(
        method = HttpMethod.Get,
        path = "comics/$comicId/comments",
        query = mapOf("page" to page.toString()),
        serializer = CommentListData.serializer(),
    )

    /** `GET comments/{commentId}/childrens`，楼中楼。 */
    suspend fun children(commentId: String, page: Int = 1): com.picacompose.picaapi.model.Paged<Comment> =
        client.call(
            method = HttpMethod.Get,
            path = "comments/$commentId/childrens",
            query = mapOf("page" to page.toString()),
            serializer = CommentListData.serializer(),
        ).comments

    /** `POST comics/{comicId}/comments`，发评论。 */
    suspend fun post(comicId: String, content: String) {
        val body = client.json.encodeToString(CommentRequest.serializer(), CommentRequest(content))
        client.callWithoutResult(
            method = HttpMethod.Post,
            path = "comics/$comicId/comments",
            bodyJson = body,
        )
    }

    /** `POST comments/{commentId}`，回复某条评论（楼中楼）。 */
    suspend fun reply(commentId: String, content: String) {
        val body = client.json.encodeToString(CommentRequest.serializer(), CommentRequest(content))
        client.callWithoutResult(
            method = HttpMethod.Post,
            path = "comments/$commentId",
            bodyJson = body,
        )
    }

    /** `GET users/my-comments`，我发过的评论（带所在漫画的标题）。 */
    suspend fun myComments(page: Int = 1): Paged<MyComment> = client.call(
        method = HttpMethod.Get,
        path = "users/my-comments",
        query = mapOf("page" to page.toString()),
        serializer = MyCommentsData.serializer(),
    ).comments

    /** `POST comments/{commentId}/hide`，隐藏评论（自己的评论或管理员）。 */
    suspend fun hide(commentId: String): String? = client.call(
        method = HttpMethod.Post,
        path = "comments/$commentId/hide",
        serializer = MessageData.serializer(),
    ).message

    /** `POST comments/{commentId}/report`，举报评论。 */
    suspend fun report(commentId: String): String? = client.call(
        method = HttpMethod.Post,
        path = "comments/$commentId/report",
        serializer = MessageData.serializer(),
    ).message

    /** `POST comments/{commentId}/top`，主评论置顶 / 取消置顶，返回是否已置顶。 */
    suspend fun top(commentId: String): Boolean = client.call(
        method = HttpMethod.Post,
        path = "comments/$commentId/top",
        serializer = TopCommentData.serializer(),
    ).isTop

    /** `POST comments/{commentId}/like`，给评论点赞 / 取消。 */
    suspend fun like(commentId: String): String? = client.call(
        method = HttpMethod.Post,
        path = "comments/$commentId/like",
        serializer = ActionData.serializer(),
    ).action
}
