package com.picacompose.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.picacompose.picaapi.PicaClient
import com.picacompose.picaapi.PicaException
import com.picacompose.picaapi.model.UserProfile

/**
 * 登录页的状态。
 *
 * 没上 ViewModel：登录页只有一个动作，`remember { LoginController(pica) }` 足够，
 * 也不用为三端各配一套 lifecycle-viewmodel 依赖。
 */
class LoginController(private val client: PicaClient) {

    var email by mutableStateOf("")
        private set

    var password by mutableStateOf("")
        private set

    var passwordVisible by mutableStateOf(false)
        private set

    var loading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    /** 登录成功后的用户资料，非空即代表已登录。 */
    var profile by mutableStateOf<UserProfile?>(null)
        private set

    val canSubmit: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && !loading

    fun onEmailChange(value: String) {
        email = value
        error = null
    }

    fun onPasswordChange(value: String) {
        password = value
        error = null
    }

    fun togglePasswordVisible() {
        passwordVisible = !passwordVisible
    }

    suspend fun signIn() {
        if (!canSubmit) return
        loading = true
        error = null
        try {
            client.auth.signIn(email.trim(), password)
            // token 已经进了 client，顺手拉一次资料，好让首页有名字可显示。
            profile = runCatching { client.users.profile() }
                .getOrElse { UserProfile(name = email.trim()) }
        } catch (e: PicaException) {
            error = e.toUserMessage()
        } catch (e: Exception) {
            error = e.message?.takeIf { it.isNotBlank() } ?: "网络不太稳，稍后再试"
        } finally {
            loading = false
        }
    }

    fun signOut() {
        client.token.value = ""
        profile = null
        password = ""
        error = null
    }
}

private fun PicaException.toUserMessage(): String {
    val raw = message?.trim().orEmpty()
    return when (errorCode) {
        // 1004 invalid email or password
        "1004" -> "邮箱或密码不对"
        // 1002 validation error，detail 里是具体哪一项不合法
        "1002" -> raw.ifBlank { "请检查邮箱和密码" }
        "1005" -> "登录已过期，重新登录一下"
        else -> when {
            httpCode == 401 -> "邮箱或密码不对"
            httpCode == 400 && raw.isNotBlank() -> raw
            httpCode != null -> "登录失败（HTTP $httpCode）"
            else -> raw.ifBlank { "网络不太稳，稍后再试" }
        }
    }
}
