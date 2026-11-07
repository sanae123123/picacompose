package com.picacompose.picaapi.api

import com.picacompose.picaapi.PicaClient
import com.picacompose.picaapi.model.ForgotPasswordData
import com.picacompose.picaapi.model.ForgotPasswordRequest
import com.picacompose.picaapi.model.RegisterRequest
import com.picacompose.picaapi.model.ResetPasswordData
import com.picacompose.picaapi.model.ResetPasswordRequest
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

    /**
     * `POST auth/forgot-password`，找回密码第一步：提交邮箱，服务端返回三个密保问题。
     *
     * 下一步用 [resetPassword] 交答案换新密码。
     */
    suspend fun forgotPassword(email: String): ForgotPasswordData {
        val body = client.json.encodeToString(
            ForgotPasswordRequest.serializer(),
            ForgotPasswordRequest(email),
        )
        return client.call(
            method = HttpMethod.Post,
            path = "auth/forgot-password",
            bodyJson = body,
            authorized = false,
            serializer = ForgotPasswordData.serializer(),
        )
    }

    /**
     * `POST auth/reset-password`，找回密码第二步：密保问题编号 + 答案，返回新密码。
     *
     * @param questionNo 1 / 2 / 3，对应 [ForgotPasswordData] 里的 question1~3
     */
    suspend fun resetPassword(email: String, questionNo: Int, answer: String): String? {
        val body = client.json.encodeToString(
            ResetPasswordRequest.serializer(),
            ResetPasswordRequest(email = email, questionNo = questionNo, answer = answer),
        )
        return client.call(
            method = HttpMethod.Post,
            path = "auth/reset-password",
            bodyJson = body,
            authorized = false,
            serializer = ResetPasswordData.serializer(),
        ).password
    }

    /** 清掉本地 token。 */
    fun signOut() = client.updateToken(null)
}
