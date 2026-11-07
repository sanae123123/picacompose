package com.picacompose.picaapi.api

import com.picacompose.picaapi.PicaClient
import com.picacompose.picaapi.model.SignInData
import com.picacompose.picaapi.model.SignInRequest
import io.ktor.http.HttpMethod

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

    /** 清掉本地 token。 */
    fun signOut() = client.updateToken(null)
}
