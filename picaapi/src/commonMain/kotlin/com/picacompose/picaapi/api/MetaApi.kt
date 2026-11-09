package com.picacompose.picaapi.api

import com.picacompose.picaapi.PicaClient
import com.picacompose.picaapi.model.Announcement
import com.picacompose.picaapi.model.AnnouncementsData
import com.picacompose.picaapi.model.ApplicationDoc
import com.picacompose.picaapi.model.ApplicationsData
import com.picacompose.picaapi.model.Banner
import com.picacompose.picaapi.model.BannerListData
import com.picacompose.picaapi.model.Category
import com.picacompose.picaapi.model.CategoryListData
import com.picacompose.picaapi.model.ChatData
import com.picacompose.picaapi.model.ChatRoom
import com.picacompose.picaapi.model.InitData
import com.picacompose.picaapi.model.KeywordData
import com.picacompose.picaapi.model.Paged
import com.picacompose.picaapi.model.PicaApp
import com.picacompose.picaapi.model.PicaAppsData
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

    /** `GET chat`，聊天室列表（消息协议是 socket.io，不在 REST 里）。 */
    suspend fun chat(): List<ChatRoom> = client.call(
        method = HttpMethod.Get,
        path = "chat",
        serializer = ChatData.serializer(),
    ).chatList

    /** `GET pica-apps`，官方推荐的小应用入口。 */
    suspend fun picaApps(): List<PicaApp> = client.call(
        method = HttpMethod.Get,
        path = "pica-apps",
        serializer = PicaAppsData.serializer(),
    ).apps

    /** `GET announcements`，公告（分页）。 */
    suspend fun announcements(page: Int = 1): Paged<Announcement> = client.call(
        method = HttpMethod.Get,
        path = "announcements",
        query = mapOf("page" to page.toString()),
        serializer = AnnouncementsData.serializer(),
    ).announcements

    /** `GET applications?platform=android`，版本更新记录。 */
    suspend fun applications(page: Int = 1, platform: String = "android"): Paged<ApplicationDoc> =
        client.call(
            method = HttpMethod.Get,
            path = "applications",
            query = mapOf("platform" to platform, "page" to page.toString()),
            serializer = ApplicationsData.serializer(),
        ).applications

    /** `GET keywords`，热搜词。 */
    suspend fun keywords(): List<String> = client.call(
        method = HttpMethod.Get,
        path = "keywords",
        serializer = KeywordData.serializer(),
    ).keywords

    /**
     * 官方启动页那个「waka init」：`GET http://206.189.95.169/init`，返回
     * `{"status","addresses","waka","adKeyword"}`，`waka` 就是广告页域名。
     *
     * 原版拿到后写进 `KEY_AD_URL_BASE`，之后所有广告页（`/android/font` 等）都拼在它后面；
     * 客户端里写死的 `pica-juicy.picacomic.com` 只是拉不到时的默认值。
     */
    suspend fun adsBaseUrl(): String? {
        val text = client.absoluteGet(WAKA_INIT_URL) ?: return null
        return runCatching {
            client.json.parseToJsonElement(text).jsonObject["waka"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.trim()?.takeIf { it.startsWith("http") }
    }
}

/** 原版 `RestWakaClient` 里写死的启动配置地址。 */
private const val WAKA_INIT_URL = "http://206.189.95.169/init"
