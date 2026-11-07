package com.picacompose.picaapi.api

import com.picacompose.picaapi.PicaClient
import com.picacompose.picaapi.model.RegisterRequest
import com.picacompose.picaapi.model.SignInData
import com.picacompose.picaapi.model.SignInRequest
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonElement

/** 登录相关接口。 */
class AuthApi internal constructor(private val client: PicaClient) {

    /** `POST auth/sign-in`，成功后客户端自动保存 token。 */
    suspend fun signIn(email: String, password: String): SignInData {
        val body = client.json.encodeToString(SignInRequest.serializer(), SignInRequest(email, password))
        val data = client.call(
            method = HttpMethod.Post,
            path = "auth/sign-in",
            bodyJson = body,
            authorized = false,
            serializer = SignInData.serializer(),
        )
        if (data.token.isNotBlank()) client.updateToken(data.token)
        return data
    }

    /**
     * `POST auth/register`，注册新账号。
     *
     * 服务端只回 `{ code, message }` 这种「没有 data」的信封，所以直接返回原始 JSON，
     * 成功与否靠外层 `code == 200` 判断；邮箱重复时是 400 / error=`1008`。
     * 服务端有问题时会抛 [com.picacompose.picaapi.PicaException]。
     */
    suspend fun register(
        email: String,
        password: String,
        name: String,
        birthday: String,
        gender: String,
        question1: String,
        answer1: String,
        question2: String,
        answer2: String,
        question3: String,
        answer3: String,
    ): JsonElement {
        val body = client.json.encodeToString(
            RegisterRequest.serializer(),
            RegisterRequest(
                email = email,
                password = password,
                name = name,
                birthday = birthday,
                gender = gender,
                question1 = question1,
                answer1 = answer1,
                question2 = question2,
                answer2 = answer2,
                question3 = question3,
                answer3 = answer3,
            ),
        )
        return client.callRaw(
            method = HttpMethod.Post,
            path = "auth/register",
            bodyJson = body,
            authorized = false,
        )
    }

    /** 清掉本地 token。 */
    fun signOut() = client.updateToken(null)
}
