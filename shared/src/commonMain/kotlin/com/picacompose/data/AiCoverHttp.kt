package com.picacompose.data

/**
 * 分类磁贴的封面也走本机那个小服务：`/aicover/<art>`。
 *
 * 绕这一圈 http 是为了少改东西：磁贴本来就用 coil 加载网络图，这里换个地址就行，
 * 不用给磁贴单独写一套「异步生成 + 生成完再刷新」。
 * 地址里的 `v` 是生成计数 —— 后台画好一张它就变，coil 才会重新去拉。
 */
object AiCoverHttp {

    private const val PREFIX = "aicover/"

    /** 磁贴该请求的地址；服务没起来时返回 null，磁贴照旧用包里的图。 */
    fun url(art: String): String? {
        val port = LocalHttpServer.port.value ?: return null
        // 读一下版本号：画好新图时这里会变，磁贴跟着重组、换个地址重新拉。
        val version = AiCover.version
        return "http://127.0.0.1:$port/$PREFIX$art?v=$version"
    }

    /**
     * 挂在 [LocalHttpServer] 上：是封面就自己答，不是就返回 null 交给帮助页。
     * 只发已经画好的图 —— 没画好就 404，磁贴先空着；出图那一刻地址里的 v 会变，
     * 磁贴换地址重来就有了。请求不在这里排队等引擎，不然七块磁贴一起进来就超时了。
     */
    suspend fun handle(path: String): LocalHttpResponse? {
        val name = path.trimStart('/').substringBefore('?')
        if (!name.startsWith(PREFIX)) return null
        val art = name.removePrefix(PREFIX)
        if (art.isBlank() || art.contains("..")) return null
        val bytes = AiCover.cached(art) ?: return null
        return LocalHttpResponse(bytes, "image/png")
    }
}
