package com.picacompose.picaapi

import com.picacompose.picaapi.model.ComicDetail
import com.picacompose.picaapi.model.ComicPage
import com.picacompose.picaapi.model.Thumbnail
import com.picacompose.picaapi.model.UserProfile

/**
 * 拼装图片绝对地址，逻辑与官方客户端 `utils.g.b()` 一致：
 * 1. loremPixel 占位图原样返回；
 * 2. 配置了 imageServer 就用它；
 * 3. 否则 `fileServer/static/path`。
 */
object PicaImage {

    fun url(thumbnail: Thumbnail?, imageServer: String? = null): String? {
        val fileServer = thumbnail?.fileServer ?: return null
        if (fileServer.equals("http://lorempixel.com", ignoreCase = true)) {
            return fileServer + (thumbnail.path ?: "")
        }
        val path = thumbnail.path ?: return null
        return if (!imageServer.isNullOrBlank()) {
            imageServer + path
        } else {
            "$fileServer/static/$path"
        }
    }

    fun pageUrl(page: ComicPage?, imageServer: String? = null): String? = url(page?.media, imageServer)

    fun coverUrl(detail: ComicDetail?, imageServer: String? = null): String? = url(detail?.thumb, imageServer)

    fun avatarUrl(profile: UserProfile?, imageServer: String? = null): String? = url(profile?.avatar, imageServer)
}
