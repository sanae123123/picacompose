package com.picacompose.data

/**
 * 应用内的使用帮助：一页 HTML，由 [LocalHttpServer] 在本机端口上喂给内置浏览器。
 *
 * 为什么做成网页：内容长、要锚点跳转和折叠，写成 HTML 最省事；这一页不依赖网络，
 * 断网、图床挂了也照样能翻。也没有引文档站框架（VitePress / docsify 那些）：
 * 它们的产物要几百 KB 到几 MB 的 JS，还得先有 Node 构建环境，对「跟着 APK 走的离线页面」不划算。
 *
 * 页面和配图放在仓库根的 `help/` 目录（构建时当资源打进包），不写成 Kotlin 字符串：
 * HTML 照普通网页写、浏览器能直接打开预览，也不用为几百行标记去动编译器的常量池。
 */
object HelpPages {

    /** 交给 [LocalHttpServer] 的处理器。 */
    val handler: LocalHttpHandler = { path ->
        val name = path.trimStart('/').substringBefore('?')
        when {
            name.isEmpty() || name == "index.html" -> LocalHttpResponse.html(page())
            // 页面里的配图走相对路径，落到这里；只放行这几种，别的一律 404。
            name.contains("..") || ASSET_EXTENSIONS.none { name.endsWith(it, ignoreCase = true) } ->
                null
            else -> readHelpFile(name)?.let { LocalHttpResponse(it, contentTypeOf(name)) }
        }
    }

    /** 版本号由代码填，改版本时不用再进 HTML 里找一遍。 */
    private fun page(): String {
        val html = readHelpFile("index.html")?.decodeToString()
        return html?.replace(VERSION_TOKEN, APP_VERSION)
            ?: "<!doctype html><meta charset=\"utf-8\"><p>帮助页没打进安装包里，重装一次试试。</p>"
    }

    private fun contentTypeOf(name: String): String = when {
        name.endsWith(".html") -> "text/html; charset=utf-8"
        name.endsWith(".jpg", ignoreCase = true) || name.endsWith(".jpeg", ignoreCase = true) ->
            "image/jpeg"
        name.endsWith(".png", ignoreCase = true) -> "image/png"
        name.endsWith(".css") -> "text/css; charset=utf-8"
        name.endsWith(".js") -> "text/javascript; charset=utf-8"
        else -> "application/octet-stream"
    }

    private const val VERSION_TOKEN = "{{VERSION}}"

    private val ASSET_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".css", ".js")
}
