package com.picacompose.picaapi.api

import com.picacompose.picaapi.PicaClient
import com.picacompose.picaapi.model.ChangePasswordRequest
import com.picacompose.picaapi.model.NotificationDoc
import com.picacompose.picaapi.model.NotificationsData
import com.picacompose.picaapi.model.Paged
import com.picacompose.picaapi.model.PunchInData
import com.picacompose.picaapi.model.SloganRequest
import com.picacompose.picaapi.model.UserProfile
import com.picacompose.picaapi.model.UserProfileData
import io.ktor.http.HttpMethod

/** 用户相关接口。 */
class UserApi internal constructor(private val client: PicaClient) {

    /** `GET users/profile`，当前登录用户资料（需要 token）。 */
    suspend fun profile(): UserProfile = client.call(
        method = HttpMethod.Get,
        path = "users/profile",
        serializer = UserProfileData.serializer(),
    ).user

    /** `GET users/{userId}/profile`，指定用户资料。 */
    suspend fun profileById(userId: String): UserProfile = client.call(
        method = HttpMethod.Get,
        path = "users/$userId/profile",
        serializer = UserProfileData.serializer(),
    ).user

    /** `GET users/notifications`，消息通知（评论、点赞、系统消息都在这里）。 */
    suspend fun notifications(page: Int = 1): Paged<NotificationDoc> = client.call(
        method = HttpMethod.Get,
        path = "users/notifications",
        query = mapOf("page" to page.toString()),
        serializer = NotificationsData.serializer(),
    ).notifications

    /** `PUT users/profile`，改个人签名。 */
    suspend fun updateSlogan(slogan: String) {
        val body = client.json.encodeToString(SloganRequest.serializer(), SloganRequest(slogan))
        client.callWithoutResult(
            method = HttpMethod.Put,
            path = "users/profile",
            bodyJson = body,
        )
    }

    /** `PUT users/password`，改密码。旧密码字段名是 `old_password`。 */
    suspend fun changePassword(oldPassword: String, newPassword: String) {
        val body = client.json.encodeToString(
            ChangePasswordRequest.serializer(),
            ChangePasswordRequest(oldPassword = oldPassword, password = newPassword),
        )
        client.callWithoutResult(
            method = HttpMethod.Put,
            path = "users/password",
            bodyJson = body,
        )
    }

    /** `POST users/punch-in`，签到。返回状态字符串（如 `ok`）。 */
    suspend fun punchIn(): String? = client.call(
        method = HttpMethod.Post,
        path = "users/punch-in",
        serializer = PunchInData.serializer(),
    ).res?.status
}
