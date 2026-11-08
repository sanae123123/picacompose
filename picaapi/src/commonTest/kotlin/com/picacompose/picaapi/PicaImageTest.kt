package com.picacompose.picaapi

import com.picacompose.picaapi.model.Thumbnail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PicaImageTest {

    private fun thumb(fileServer: String, path: String) =
        Thumbnail(fileServer = fileServer, path = path)

    @Test
    fun `裸域名的 fileServer 补 static`() {
        assertEquals(
            "https://storage1.picacomic.com/static/8cd.jpg",
            PicaImage.url(thumb("https://storage1.picacomic.com", "8cd.jpg")),
        )
        assertEquals(
            "https://storage-b.picacomic.com/static/tobs/abc.jpg",
            PicaImage.url(thumb("https://storage-b.picacomic.com", "tobs/abc.jpg")),
        )
    }

    @Test
    fun `已经带静态目录的 fileServer 不再补 static`() {
        assertEquals(
            "https://diwodiwo.xyz/static/old.jpg",
            PicaImage.url(thumb("https://diwodiwo.xyz/static", "old.jpg")),
        )
        assertEquals(
            "https://file.bidobido.xyz/images/star.jpg?v=3",
            PicaImage.url(thumb("https://file.bidobido.xyz/images", "star.jpg?v=3")),
        )
    }

    @Test
    fun `path 是绝对地址时原样返回`() {
        assertEquals(
            "https://example.com/a.jpg",
            PicaImage.url(thumb("https://storage1.picacomic.com", "https://example.com/a.jpg")),
        )
    }

    @Test
    fun `配置了 imageServer 时优先用它`() {
        assertEquals(
            "https://img.example.com/static/old.jpg",
            PicaImage.url(thumb("https://storage1.picacomic.com", "old.jpg"), "https://img.example.com/static"),
        )
    }

    @Test
    fun `缺字段返回 null`() {
        assertNull(PicaImage.url(null))
        assertNull(PicaImage.url(thumb("", "a.jpg")))
        assertNull(PicaImage.url(thumb("https://storage1.picacomic.com", "")))
    }
}
