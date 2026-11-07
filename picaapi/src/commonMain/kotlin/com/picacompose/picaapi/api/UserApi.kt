package com.picacompose.picaapi.api

import com.picacompose.picaapi.PicaClient
import com.picacompose.picaapi.model.AvatarData
import com.picacompose.picaapi.model.AvatarRequest
import com.picacompose.picaapi.model.ChangePasswordRequest
import com.picacompose.picaapi.model.DirtyData
import com.picacompose.picaapi.model.NotificationDoc
import com.picacompose.picaapi.model.NotificationsData
import com.picacompose.picaapi.model.Paged
import com.picacompose.picaapi.model.PunchInData
import com.picacompose.picaapi.model.SloganRequest
import com.picacompose.picaapi.model.Thumbnail
import com.picacompose.picaapi.model.TitleRequest
import com.picacompose.picaapi.model.UpdatePicaIdRequest
import com.picacompose.picaapi.model.UpdateQandARequest
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

    /**
     * `POST users/{id}/dirty`，查这个用户资料是否「待更新」（官方改 ID 流程用）。
     * 名字看着像写操作，官方用途是查询，返回 `{dirty: boolean}`。
     */
    suspend fun isDirty(userId: String): Boolean = client.call(
        method = HttpMethod.Post,
        path = "users/$userId/dirty",
        serializer = DirtyData.serializer(),
    ).dirty

    /** `PUT users/update-id`，改登录邮箱 / 昵称（官方叫 picaId）。 */
    suspend fun updatePicaId(email: String, name: String) {
        val body = client.json.encodeToString(
            UpdatePicaIdRequest.serializer(),
            UpdatePicaIdRequest(email = email, name = name),
        )
        client.callWithoutResult(method = HttpMethod.Put, path = "users/update-id", bodyJson = body)
    }

    /** `PUT users/update-qa`，更新三组密保问题。 */
    suspend fun updateQandA(
        question1: String,
        answer1: String,
        question2: String,
        answer2: String,
        question3: String,
        answer3: String,
    ) {
        val body = client.json.encodeToString(
            UpdateQandARequest.serializer(),
            UpdateQandARequest(
                question1 = question1,
                answer1 = answer1,
                question2 = question2,
                answer2 = answer2,
                question3 = question3,
                answer3 = answer3,
            ),
        )
        client.callWithoutResult(method = HttpMethod.Put, path = "users/update-qa", bodyJson = body)
    }

    /** `PUT users/avatar`，换头像；`avatar` 是图片地址或 base64。返回新头像缩略图。 */
    suspend fun updateAvatar(avatar: String): Thumbnail? {
        val body = client.json.encodeToString(AvatarRequest.serializer(), AvatarRequest(avatar))
        return client.call(
            method = HttpMethod.Put,
            path = "users/avatar",
            bodyJson = body,
            serializer = AvatarData.serializer(),
        ).avatar
    }

    /** `PUT users/{id}/title`，改用户头衔。 */
    suspend fun updateTitle(userId: String, title: String) {
        val body = client.json.encodeToString(TitleRequest.serializer(), TitleRequest(title))
        client.callWithoutResult(
            method = HttpMethod.Put,
            path = "users/$userId/title",
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
