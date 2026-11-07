package com.picacompose.picaapi.api

import com.picacompose.picaapi.PicaClient
import com.picacompose.picaapi.model.PunchInData
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

    /** `POST users/punch-in`，签到。返回状态字符串（如 `ok`）。 */
    suspend fun punchIn(): String? = client.call(
        method = HttpMethod.Post,
        path = "users/punch-in",
        serializer = PunchInData.serializer(),
    ).res?.status
}
