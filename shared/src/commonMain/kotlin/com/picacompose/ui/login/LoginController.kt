package com.picacompose.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.picacompose.data.LineRouter
import com.picacompose.data.SessionStore
import com.picacompose.picaapi.PicaClient
import com.picacompose.picaapi.PicaException
import com.picacompose.picaapi.model.UserProfile

/**
 * 登录页的状态。
 *
 * 没上 ViewModel：登录流程只有几个动作，`remember { LoginController({ pica }) }` 足够，
 * 也不用为三端各配一套 lifecycle-viewmodel 依赖。
 *
 * 拿的是取客户端的 lambda 而不是实例：换线路时全局 `pica` 会被整个换掉，
 * 存着旧实例的话这个页面永远走老线路。
 */
class LoginController(
    private val client: () -> PicaClient,
    private val session: SessionStore = SessionStore(),
) {

    /** 记住过账号就先填上，省得每次敲。 */
    var email by mutableStateOf(session.savedAccount.orEmpty())
        private set

    var password by mutableStateOf("")
        private set

    var passwordVisible by mutableStateOf(false)
        private set

    /** 勾着「记住账号」就把 token 存下来，下次开 App 直接进首页。 */
    var rememberAccount by mutableStateOf(true)
        private set

    var loading by mutableStateOf(false)
        private set

    /** 启动时正在用旧 token 恢复会话，界面上给个加载态。 */
    var restoring by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    /** 登录成功后的用户资料，非空即代表已登录。 */
    var profile by mutableStateOf<UserProfile?>(null)
        private set

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

    fun onRememberChange(value: Boolean) {
        rememberAccount = value
    }

    /**
     * 开 App 时试一下上次存的 token 还能不能用：能用就直接进首页，不用再登一次。
     * 只有明确「token 失效」才清掉，网络抖动时留着下次再试。
     */
    suspend fun restoreSession() {
        val token = session.savedToken ?: return
        restoring = true
        client().updateToken(token)
        try {
            profile = loadProfileWithFailover()
        } catch (e: PicaException) {
            if (e.errorCode == "1005" || e.httpCode == 401) {
                client().updateToken("")
                session.clearToken()
                error = "上次的登录过期了，重新登录一下"
            } else {
                error = e.toUserMessage()
            }
        } catch (e: Exception) {
            error = "网络不太稳，稍后再试"
        } finally {
            restoring = false
        }
    }

    /**
     * 拉资料；连不上时等后台测速给出结论，换一条线路再试一次。
     *
     * 走缓存线路直接进主界面时，这条请求就是第一个撞上坏线路的，兜住它才不会把
     * 重新打开 App 的人丢回登录页。
     */
    private suspend fun loadProfileWithFailover(): UserProfile {
        val before = LineRouter.switchCount
        return try {
            client().users.profile()
        } catch (e: Exception) {
            // 只有「连不上」才值得换线路，HTTP 4xx/5xx 是服务端在说话。
            if (e is PicaException && e.httpCode != null) throw e
            val switched = LineRouter.recover() || LineRouter.switchCount != before
            if (!switched) throw e
            client().users.profile()
        }
    }

    suspend fun signIn() {
        if (loading) return
        if (email.isBlank()) {
            error = "先填邮箱或用户名"
            return
        }
        if (password.isBlank()) {
            error = "密码还没填"
            return
        }
        loading = true
        error = null
        try {
            val signedIn = client().auth.signIn(email.trim(), password)
            val account = email.trim()
            if (rememberAccount) {
                session.save(signedIn.token, account)
            } else {
                session.clearAll()
            }
            // token 已经进了 client，顺手拉一次资料，好让首页有名字可显示。
            profile = runCatching { client().users.profile() }
                .getOrElse { UserProfile(name = account) }
        } catch (e: PicaException) {
            error = e.toUserMessage()
        } catch (e: Exception) {
            error = e.message?.takeIf { it.isNotBlank() } ?: "网络不太稳，稍后再试"
        } finally {
            loading = false
        }
    }

    /**
     * 重新拉一次资料。
     *
     * 登录时拉的那份会一直用到下次登录，里面的 `isPunched`、`exp` 早就过期了；
     * 进「我的」页时重拉一次，签到状态才是服务器上的真实值。
     */
    suspend fun refreshProfile(): UserProfile? {
        val fresh = runCatching { client().users.profile() }.getOrNull() ?: return null
        profile = fresh
        return fresh
    }

    fun signOut() {
        client().updateToken("")
        session.clearToken()
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
