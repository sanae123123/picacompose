package com.picacompose.picaapi

import com.picacompose.picaapi.model.ComicListData
import com.picacompose.picaapi.model.Envelope
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
    fun imageServerOverridesFileServer() {
        val thumb = Thumbnail(fileServer = "https://s3.picacomic.com", path = "/img/001.jpg")
        assertEquals("https://cdn.example.com/img/001.jpg", PicaImage.url(thumb, "https://cdn.example.com"))
        assertNull(PicaImage.url(Thumbnail(), null))
    }
}
