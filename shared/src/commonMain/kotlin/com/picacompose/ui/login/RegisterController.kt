package com.picacompose.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.picacompose.picaapi.PicaClient
import com.picacompose.picaapi.PicaException

/**
 * 注册页的状态。
 *
 * 走的是 PicACG 官方的 `auth/register`：邮箱、密码、昵称、生日、性别，外加三个密保问题和
 * 答案 —— 少了密保问题，以后找回密码那一步就没东西可答，所以这里也是必填。
 */
class RegisterController(private val client: () -> PicaClient) {

    var name by mutableStateOf("")
        private set

    var email by mutableStateOf("")
        private set

    var password by mutableStateOf("")
        private set

    var passwordVisible by mutableStateOf(false)
        private set

    /** 生日必须是 `yyyy-MM-dd`，服务端就认这个格式。 */
    var birthday by mutableStateOf("")
        private set

    /** `m` / `f` / `bot`（机器人），跟官方一致的三个取值。 */
    var gender by mutableStateOf("m")
        private set

    var question1 by mutableStateOf("")
        private set
    var answer1 by mutableStateOf("")
        private set
    var question2 by mutableStateOf("")
        private set
    var answer2 by mutableStateOf("")
        private set
    var question3 by mutableStateOf("")
        private set
    var answer3 by mutableStateOf("")
        private set

    var loading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    fun onNameChange(value: String) {
        name = value
        error = null
    }

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

    fun onBirthdayChange(value: String) {
        birthday = value
        error = null
    }

    fun onGenderChange(value: String) {
        gender = value
        error = null
    }

    fun onQuestion1Change(value: String) {
        question1 = value
        error = null
    }

    fun onAnswer1Change(value: String) {
        answer1 = value
        error = null
    }

    fun onQuestion2Change(value: String) {
        question2 = value
        error = null
    }

    fun onAnswer2Change(value: String) {
        answer2 = value
        error = null
    }

    fun onQuestion3Change(value: String) {
        question3 = value
        error = null
    }

    fun onAnswer3Change(value: String) {
        answer3 = value
        error = null
    }

    /**
     * 提交注册；成功回调 [onRegistered]（带上邮箱，回去登录时能直接填好）。
     *
     * 服务端成功时只回 `{ code, message }`，没有 data，所以这里只关心有没有抛异常。
     */
    suspend fun submit(onRegistered: (String) -> Unit) {
        if (loading) return
        validate()?.let {
            error = it
            return
        }
        loading = true
        error = null
        try {
            client().auth.register(
                email = email.trim(),
                password = password,
                name = name.trim(),
                birthday = birthday.trim(),
                gender = gender,
                question1 = question1.trim(),
                answer1 = answer1.trim(),
                question2 = question2.trim(),
                answer2 = answer2.trim(),
                question3 = question3.trim(),
                answer3 = answer3.trim(),
            )
            onRegistered(email.trim())
        } catch (e: PicaException) {
            error = e.registerMessage()
        } catch (e: Exception) {
            error = e.message?.takeIf { it.isNotBlank() } ?: "网络不太稳，稍后再试"
        } finally {
            loading = false
        }
    }

    /** 本地先挡一道，别把明显填错的东西发出去等服务器回 400。 */
    private fun validate(): String? = when {
        name.isBlank() -> "先填个昵称"
        !EMAIL.matches(email.trim()) -> "邮箱格式看着不对"
        password.length < 8 -> "密码至少 8 位"
        !BIRTHDAY.matches(birthday.trim()) -> "生日按 1998-05-20 这样写"
        !validDate(birthday.trim()) -> "生日这个日期不存在，检查一下"
        question1.isBlank() || answer1.isBlank() ->
            "第一个密保问题和答案都要填（找回密码要用）"
        question2.isBlank() || answer2.isBlank() ->
            "第二个密保问题和答案都要填"
        question3.isBlank() || answer3.isBlank() ->
            "第三个密保问题和答案都要填"
        else -> null
    }

    private fun validDate(text: String): Boolean {
        val parts = text.split('-')
        if (parts.size != 3) return false
        val month = parts[1].toIntOrNull() ?: return false
        val day = parts[2].toIntOrNull() ?: return false
        return month in 1..12 && day in 1..31
    }

    private companion object {
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        val BIRTHDAY = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    }
}

/** 注册接口的错误翻译：邮箱重复是最常见的那一个。 */
private fun PicaException.registerMessage(): String {
    val raw = message?.trim().orEmpty()
    return when (errorCode) {
        // 1008 官方表示「邮箱已存在」
        "1008" -> "这个邮箱已经注册过了，直接登录或者走找回密码"
        "1002" -> raw.ifBlank { "有些信息填得不对，检查一下再试" }
        else -> when {
            httpCode == 400 && raw.isNotBlank() -> raw
            httpCode == null -> raw.ifBlank { "连不上服务器，稍后再试" }
            else -> raw.ifBlank { "注册失败（HTTP $httpCode）" }
        }
    }
}
