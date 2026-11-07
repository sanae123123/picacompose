package com.picacompose.picaapi.api

import com.picacompose.picaapi.PicaClient
import com.picacompose.picaapi.model.Banner
import com.picacompose.picaapi.model.BannerListData
import com.picacompose.picaapi.model.Category
import com.picacompose.picaapi.model.CategoryListData
import com.picacompose.picaapi.model.InitData
import com.picacompose.picaapi.model.KeywordData
import io.ktor.http.HttpMethod

/** 初始化与元数据接口。 */
class MetaApi internal constructor(private val client: PicaClient) {

    /** `GET init?platform=android`，启动时拉分类、图片服务器、签到状态等。 */
    suspend fun init(): InitData {
        val data = client.call(
            method = HttpMethod.Get,
            path = "init",
            query = mapOf("platform" to "android"),
            serializer = InitData.serializer(),
        )
        client.updateImageServer(data.imageServer)
        return data
    }

    /** `GET categories`，全部分类。 */
    suspend fun categories(): List<Category> = client.call(
        method = HttpMethod.Get,
        path = "categories",
        serializer = CategoryListData.serializer(),
    ).categories

    /** `GET banners`，首页轮播。 */
    suspend fun banners(): List<Banner> = client.call(
        method = HttpMethod.Get,
        path = "banners",
        serializer = BannerListData.serializer(),
    ).banners

    /** `GET keywords`，热搜词。 */
    suspend fun keywords(): List<String> = client.call(
        method = HttpMethod.Get,
        path = "keywords",
        serializer = KeywordData.serializer(),
    ).keywords
}
