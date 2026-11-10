package com.picacompose.picaapi

import com.picacompose.picaapi.model.ComicDetail
import com.picacompose.picaapi.model.ComicPage
import com.picacompose.picaapi.model.Thumbnail
import com.picacompose.picaapi.model.UserProfile

/**
 * 拼装图片绝对地址。
 *
 * 接口里的 `thumb` 是 `{ fileServer, path }` 两段，但 `fileServer` 有两种形态：
 * - 裸域名（`https://storage1.picacomic.com`、`https://storage-b.picacomic.com`），
 *   真实文件在 `/static/` 下，官方客户端的 `utils.g.b()` 就是补这一层；
 * - 已经带静态目录（`https://diwodiwo.xyz/static`、`https://file.bidobido.xyz/images`），
 *   再补一次 `/static/` 会变成 `/static/static/xxx.jpg`，必然 404。
 *
 * 所以按 `fileServer` 有没有路径来拼，两种都能出图。
 */
object PicaImage {

    fun url(thumbnail: Thumbnail?, imageServer: String? = null): String? {
        val path = thumbnail?.path?.takeIf { it.isNotBlank() } ?: return null
        val fileServer = thumbnail.fileServer?.takeIf { it.isNotBlank() }
        // lorempixel 时代的占位图是拼好的绝对地址，原样返回。
        if (path.startsWith("http")) return path
        if (fileServer != null && fileServer.equals("http://lorempixel.com", ignoreCase = true)) return path
        // 官方客户端的 `utils.g.b()`：有 /init 下发的图床就优先用它，没有才回 fileServer。
        // 官方图床域名在很多网络上被解析到错的 IP，镜像图床反而是能通的那条。
        if (!imageServer.isNullOrBlank()) {
            return imageServer.trimEnd('/') + "/" + path.trimStart('/')
        }
        if (fileServer == null) return null
        val base = fileServer.trimEnd('/')
        // 游戏图标在 storage-b 上是 `tobs/<file>`，服务端 301 到去掉 `tobs/` 的地址，
        // 这里直接拼最终地址，省一次跳转。
        val relative = path.trimStart('/').removePrefix("tobs/")
        return if (hasDirectory(base)) "$base/$relative" else "$base/static/$relative"
    }

    /** `https://host/xxx` 这种带目录的 fileServer 视为已经指向静态根。 */
    private fun hasDirectory(baseUrl: String): Boolean =
        baseUrl.substringAfter("://", "").contains('/')

    fun pageUrl(page: ComicPage?, imageServer: String? = null): String? = url(page?.media, imageServer)

    fun coverUrl(detail: ComicDetail?, imageServer: String? = null): String? = url(detail?.thumb, imageServer)

    fun avatarUrl(profile: UserProfile?, imageServer: String? = null): String? = url(profile?.avatar, imageServer)
}
