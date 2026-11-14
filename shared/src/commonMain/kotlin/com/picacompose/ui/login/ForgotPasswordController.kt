package com.picacompose.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.picacompose.picaapi.PicaClient
import com.picacompose.picaapi.PicaException

/**
 * 找回密码的状态：填邮箱 → 拉回三个密保问题 → 答对一个换新密码。
 *
 * 走的是官方两步接口：`auth/forgot-password` 返回问题，`auth/reset-password` 提交
 * 问题编号 + 答案，服务端直接把新密码返回（不是发邮件的那种流程）。
 */
class ForgotPasswordController(private val client: () -> PicaClient) {

    enum class Step { Email, Answer, Done }

    var step by mutableStateOf(Step.Email)
        private set

    var email by mutableStateOf("")
        private set

    /** 服务端回来的密保问题：编号（1/2/3）到问题文本。 */
    var questions by mutableStateOf<List<Pair<Int, String>>>(emptyList())
        private set

    var chosenNo by mutableStateOf(1)
        private set

    var answer by mutableStateOf("")
        private set

    /** 重置成功后服务端给的新密码；非空就代表已经可以回去登录了。 */
    var newPassword by mutableStateOf<String?>(null)
        private set

    var loading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    /** 从登录页过来时把已经填好的邮箱带过来，省得再敲一遍。 */
    fun prefillEmail(value: String) {
        if (email.isBlank()) email = value
    }

    fun onEmailChange(value: String) {
        email = value
        error = null
    }

    fun onAnswerChange(value: String) {
        answer = value
        error = null
    }

    fun chooseQuestion(no: Int) {
        chosenNo = no
        error = null
    }

    /** 换一个邮箱重来：问题列表和答案都清掉。 */
    fun backToEmail() {
        step = Step.Email
        questions = emptyList()
        answer = ""
        error = null
        loading = false
    }

    suspend fun submitEmail() {
        if (loading) return
        if (!EMAIL.matches(email.trim())) {
            error = "先填注册时用的邮箱"
            return
        }
        loading = true
        error = null
        try {
            val data = client().auth.forgotPassword(email.trim())
            val list = listOfNotNull(
                data.question1?.takeIf { it.isNotBlank() }?.let { 1 to it },
                data.question2?.takeIf { it.isNotBlank() }?.let { 2 to it },
                data.question3?.takeIf { it.isNotBlank() }?.let { 3 to it },
            )
            if (list.isEmpty()) {
                error = "这个邮箱没查到密保问题，确认下是不是注册时那个邮箱"
            } else {
                questions = list
                chosenNo = list.first().first
                answer = ""
                step = Step.Answer
            }
        } catch (e: PicaException) {
            error = e.forgotMessage()
        } catch (e: Exception) {
            error = e.message?.takeIf { it.isNotBlank() } ?: "网络不太稳，稍后再试"
        } finally {
            loading = false
        }
    }

    suspend fun submitAnswer() {
        if (loading) return
        if (answer.isBlank()) {
            error = "答案还没填"
            return
        }
        loading = true
        error = null
        try {
            val password = client().auth.resetPassword(email.trim(), chosenNo, answer.trim())
            if (password.isNullOrBlank()) {
                // 服务端有时候 200 但 data 里没有 password：等于答案没对上。
                error = "答案不对，再想想"
            } else {
                newPassword = password
                step = Step.Done
            }
        } catch (e: PicaException) {
            error = e.forgotMessage()
        } catch (e: Exception) {
            error = e.message?.takeIf { it.isNotBlank() } ?: "网络不太稳，稍后再试"
        } finally {
            loading = false
        }
    }

    private companion object {
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

private fun PicaException.forgotMessage(): String {
    val raw = message?.trim().orEmpty()
    return when {
        httpCode == 400 && raw.isNotBlank() -> raw
        httpCode == null -> raw.ifBlank { "连不上服务器，稍后再试" }
        errorCode == "1004" -> "这个邮箱没注册过，检查一下"
        else -> raw.ifBlank { "没办成（HTTP $httpCode），稍后再试" }
    }
}
