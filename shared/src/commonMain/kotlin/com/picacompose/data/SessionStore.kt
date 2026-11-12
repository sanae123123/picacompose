package com.picacompose.data

/** 本机记下来的一对账号 + 令牌，用来在设置里直接切过去。 */
data class SavedAccount(val account: String, val token: String)

/**
 * 登录状态的持久化：token + 上次用的账号名 + 已登录过的账号列表。
 *
 * 只存 token 不存密码；点「退出登录」时删 token、留账号名，方便下次直接补密码。
 */
class SessionStore(private val kv: KeyValueStore = platformKeyValueStore()) {

    val savedToken: String?
        get() = kv.get(KEY_TOKEN)?.takeIf { it.isNotBlank() }

    val savedAccount: String?
        get() = kv.get(KEY_ACCOUNT)?.takeIf { it.isNotBlank() }

    /**
     * 本机登录过的账号，最近用的排最前。设置页的「切换账号」就是从这里挑。
     *
     * 关掉「记住账号」时这个列表跟着清空——不落盘的承诺要对得起。
     */
    val accounts: List<SavedAccount>
        get() {
            val list = parseAccounts(kv.get(KEY_ACCOUNTS))
            // 老版本升级上来的、或者登录那会儿列表还没写进去的：当前这个号也列出来，
            // 不然「切换账号」里永远是空的。
            val account = savedAccount ?: return list
            val token = savedToken ?: return list
            if (list.any { it.account == account }) return list
            return listOf(SavedAccount(account, token)) + list
        }

    /**
     * 「记住账号」开关：关着的时候登录成功也不落盘 token，下次打开要重新登录。
     *
     * 默认开，和原来的行为一致。关掉时顺手把已存的 token 清掉（账号名也一起忘），
     * 免得开关说关着、实际上还在自动登录。
     */
    var remember: Boolean
        get() = kv.get(KEY_REMEMBER) != "0"
        set(value) {
            kv.put(KEY_REMEMBER, if (value) "1" else "0")
            if (!value) forgetTokens()
        }

    fun save(token: String, account: String) {
        kv.put(KEY_TOKEN, token)
        kv.put(KEY_ACCOUNT, account)
        if (remember && account.isNotBlank()) saveAccount(account, token)
    }

    fun rememberAccount(account: String) {
        kv.put(KEY_ACCOUNT, account)
    }

    /** 把某个账号的令牌记下来，同一个账号只留最新的一份、并挪到最前面。 */
    fun saveAccount(account: String, token: String) {
        val name = account.trim()
        if (name.isBlank() || token.isBlank()) return
        val rest = accounts.filterNot { it.account == name }
        val next = (listOf(SavedAccount(name, token)) + rest).take(MAX_ACCOUNTS)
        kv.put(KEY_ACCOUNTS, encodeAccounts(next))
    }

    fun removeAccount(account: String) {
        val name = account.trim()
        val next = accounts.filterNot { it.account == name }
        kv.put(KEY_ACCOUNTS, if (next.isEmpty()) null else encodeAccounts(next))
    }

    fun clearToken() {
        kv.put(KEY_TOKEN, null)
    }

    /** 不落令牌，但账号名留着——下次打开至少知道该登哪个号。 */
    fun forgetTokens() {
        kv.put(KEY_TOKEN, null)
        kv.put(KEY_ACCOUNTS, null)
    }

    fun clearAll() {
        kv.put(KEY_TOKEN, null)
        kv.put(KEY_ACCOUNT, null)
        kv.put(KEY_ACCOUNTS, null)
    }

    private fun parseAccounts(raw: String?): List<SavedAccount> =
        raw.orEmpty()
            .split(RECORD_SEPARATOR)
            .mapNotNull { record ->
                val split = record.indexOf(FIELD_SEPARATOR)
                if (split <= 0 || split == record.lastIndex) return@mapNotNull null
                val account = record.substring(0, split)
                val token = record.substring(split + 1)
                if (token.isBlank()) null else SavedAccount(account, token)
            }

    private fun encodeAccounts(list: List<SavedAccount>): String =
        list.joinToString(RECORD_SEPARATOR.toString()) { it.account + FIELD_SEPARATOR + it.token }

    private companion object {
        const val KEY_TOKEN = "auth.token"
        const val KEY_ACCOUNT = "auth.account"
        const val KEY_REMEMBER = "auth.remember"
        const val KEY_ACCOUNTS = "auth.accounts"

        /** 账号名和令牌里都不会出现控制字符，拿来当分隔符最省事，也不用引入序列化。 */
        const val RECORD_SEPARATOR = '\u0002'
        const val FIELD_SEPARATOR = '\u0001'

        /** 留着切换的账号不需要太多，十条够用了。 */
        const val MAX_ACCOUNTS = 10
    }
}
