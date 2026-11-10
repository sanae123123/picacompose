package com.picacompose.picaapi

import com.picacompose.picaapi.model.ComicListData
import com.picacompose.picaapi.model.Envelope
import com.picacompose.picaapi.model.GameListData
import com.picacompose.picaapi.model.PicaAppsData
import com.picacompose.picaapi.model.MyCommentsData
import com.picacompose.picaapi.model.NotificationsData
import com.picacompose.picaapi.model.Thumbnail
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun decodesComicListEnvelope() {
        val text = """
            {"code":200,"message":"success","data":{"comics":{"total":1,"limit":20,"page":1,"pages":1,
            "docs":[{"_id":"59f4b0f8e6d3e6e0f0e0e0e0","title":"测试漫画","author":"某人",
            "likesCount":12,"pagesCount":34,"epsCount":3,"finished":true,
            "categories":["全彩"],"thumb":{"fileServer":"https://s3.picacomic.com","path":"cover.jpg",
            "originalName":"cover.jpg"}}]}}}
        """.trimIndent()

        val envelope = json.decodeFromString(Envelope.serializer(ComicListData.serializer()), text)
        assertEquals(200, envelope.code)
        val comics = envelope.data!!.comics
        assertEquals(1, comics.total)
        val first = comics.docs.single()
        assertEquals("59f4b0f8e6d3e6e0f0e0e0e0", first.id)
        assertEquals("测试漫画", first.title)
        assertEquals(3, first.episodesCount)
        assertEquals(34, first.pagesCount)
        assertTrue(first.finished)
        assertEquals("https://s3.picacomic.com/static/cover.jpg", PicaImage.url(first.thumb, null))
    }

    @Test
    fun ignoresUnknownFieldsAndMissingData() {
        val text = """{"code":200,"message":"success","data":{"comics":{}},"extra":{"a":1}}"""
        val envelope = json.decodeFromString(Envelope.serializer(ComicListData.serializer()), text)
        assertEquals(0, envelope.data!!.comics.total)
        assertTrue(envelope.data!!.comics.docs.isEmpty())
    }

    @Test
    fun decodesNotificationsAndMyComments() {
        // 通知：_sender 是对象、cover 是缩略图
        val notifications = json.decodeFromString(
            Envelope.serializer(NotificationsData.serializer()),
            """
            {"code":200,"message":"success","data":{"notifications":{"total":1,"limit":20,"page":1,"pages":1,
            "docs":[{"_id":"n1","title":"有人回复了你","content":"内容","created_at":"2026-11-07T00:00:00.000Z",
            "cover":{"fileServer":"https://s3.picacomic.com","path":"c.jpg","originalName":"c.jpg"},
            "_sender":{"_id":"u1","name":"古手葵花","level":3,"avatar":{"fileServer":"https://s3.picacomic.com","path":"a.png"}},
            "system":false}]}}}
            """.trimIndent(),
        ).data!!.notifications
        assertEquals(1, notifications.total)
        assertEquals("有人回复了你", notifications.docs.single().title)
        assertEquals("古手葵花", notifications.docs.single().sender?.name)

        // 我的评论：_comic 是对象而不是字符串 id
        val mine = json.decodeFromString(
            Envelope.serializer(MyCommentsData.serializer()),
            """
            {"code":200,"message":"success","data":{"comments":{"total":1,"limit":20,"page":1,"pages":1,
            "docs":[{"_id":"c1","content":"好看","likesCount":2,"commentsCount":1,
            "_comic":{"_id":"comic1","title":"测试漫画"}}]}}}
            """.trimIndent(),
        ).data!!.comments
        assertEquals("测试漫画", mine.docs.single().comic?.title)
        assertEquals(1, mine.docs.single().childsCount)
    }

    @Test
    fun decodesGamesAndPicaApps() {
        val games = json.decodeFromString(
            Envelope.serializer(GameListData.serializer()),
            """
            {"code":200,"message":"success","data":{"games":{"total":1,"limit":20,"page":1,"pages":1,
            "docs":[{"_id":"g1","title":"星神少女","version":"1.0.0","publisher":"OP","adult":true,
            "androidLinks":["https://example.com/g1"],
            "icon":{"fileServer":"https://storage-b.picacomic.com","path":"tobs/a.png","originalName":"a.png"}}]}}}
            """.trimIndent(),
        ).data!!.games
        assertEquals("星神少女", games.docs.single().title)
        assertEquals(1, games.docs.single().androidLinks.size)

        val apps = json.decodeFromString(
            Envelope.serializer(PicaAppsData.serializer()),
            """
            {"code":200,"message":"success","data":{"apps":[{"title":"哔咔AI互动小说",
            "url":"https://example.com","icon":"https://example.com/i.jpg","showTitleBar":false,
            "description":"说明"}]}}
            """.trimIndent(),
        ).data!!.apps
        assertEquals("哔咔AI互动小说", apps.single().title)
        assertTrue(!apps.single().showTitleBar)
    }

    @Test
    fun imageServerOverridesFileServer() {
        val thumb = Thumbnail(fileServer = "https://s3.picacomic.com", path = "/img/001.jpg")
        assertEquals("https://cdn.example.com/img/001.jpg", PicaImage.url(thumb, "https://cdn.example.com"))
        assertNull(PicaImage.url(Thumbnail(), null))
    }
}
