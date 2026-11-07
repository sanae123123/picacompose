package com.picacompose.picaapi.api

import com.picacompose.picaapi.PicaClient
import com.picacompose.picaapi.model.ComicPagesData
import io.ktor.http.HttpMethod

/** 章节相关接口。 */
class EpisodeApi internal constructor(private val client: PicaClient) {

    /** `GET eps/{episodeId}/pages`，按章节 id 取图片列表。 */
    suspend fun pages(episodeId: String, page: Int = 1): ComicPagesData = client.call(
        method = HttpMethod.Get,
        path = "eps/$episodeId/pages",
        query = mapOf("page" to page.toString()),
        serializer = ComicPagesData.serializer(),
    )
}
