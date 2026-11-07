package com.picacompose.data

/**
 * 登录状态的持久化：token + 上次用的账号名。
 *
 * 只存 token 不存密码；点「退出登录」时删 token、留账号名，方便下次直接补密码。
 */
class SessionStore(private val kv: KeyValueStore = platformKeyValueStore()) {

    val savedToken: String?
        get() = kv.get(KEY_TOKEN)?.takeIf { it.isNotBlank() }

    val savedAccount: String?
        get() = kv.get(KEY_ACCOUNT)?.takeIf { it.isNotBlank() }

    fun save(token: String, account: String) {
        kv.put(KEY_TOKEN, token)
        kv.put(KEY_ACCOUNT, account)
    }

    fun rememberAccount(account: String) {
        kv.put(KEY_ACCOUNT, account)
    }

    fun clearToken() {
        kv.put(KEY_TOKEN, null)
    }

    fun clearAll() {
        kv.put(KEY_TOKEN, null)
        kv.put(KEY_ACCOUNT, null)
    }

    private companion object {
        const val KEY_TOKEN = "auth.token"
        const val KEY_ACCOUNT = "auth.account"
    }
}
