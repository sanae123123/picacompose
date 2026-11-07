package com.picacompose.picaapi.api

import com.picacompose.picaapi.PicaClient
import com.picacompose.picaapi.model.AdjustExpRequest
import com.picacompose.picaapi.model.UserIdRequest
import io.ktor.http.HttpMethod

/**
 * `utils/*` 下的管理接口：调经验、拉黑用户、删评论。
 *
 * 这些端点一般只有管理员 / 骑士账号可用，普通账号调用会返回业务错误。
 */
class UtilsApi internal constructor(private val client: PicaClient) {

    /** `POST utils/adjust-exp`，调整某用户经验值（管理员）。 */
    suspend fun adjustExp(userId: String, exp: Int) {
        val body = client.json.encodeToString(
            AdjustExpRequest.serializer(),
            AdjustExpRequest(userId = userId, exp = exp),
        )
        client.callWithoutResult(
            method = HttpMethod.Post,
            path = "utils/adjust-exp",
            bodyJson = body,
        )
    }

    /** `POST utils/block-user`，拉黑用户。 */
    suspend fun blockUser(userId: String) {
        val body = client.json.encodeToString(UserIdRequest.serializer(), UserIdRequest(userId))
        client.callWithoutResult(
            method = HttpMethod.Post,
            path = "utils/block-user",
            bodyJson = body,
        )
    }

    /** `POST utils/remove-comment`，删评论（管理员）。 */
    suspend fun removeComment(userId: String) {
        val body = client.json.encodeToString(UserIdRequest.serializer(), UserIdRequest(userId))
        client.callWithoutResult(
            method = HttpMethod.Post,
            path = "utils/remove-comment",
            bodyJson = body,
        )
    }
}
