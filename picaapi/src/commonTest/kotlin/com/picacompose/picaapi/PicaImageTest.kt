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
            // `tobs/` 是真实路径的一部分，不能裁：官方 `qe3` 只做 `fileServer + "/static/" + path`。
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
    fun `imageServer 是裸域名时同样补 static`() {
        assertEquals(
            "https://img.example.com/static/old.jpg",
            PicaImage.url(thumb("https://storage1.picacomic.com", "old.jpg"), "https://img.example.com"),
        )
    }

    @Test
    fun `文件服务上的 tobs 路径也原样保留`() {
        // fileServer 和 imageServer 两条路拼法完全一样，都保留 tobs。
        assertEquals(
            "https://s3.picacomic.com/static/tobs/8cd.jpg",
            PicaImage.url(
                thumb("https://storage-b.picacomic.com", "tobs/8cd.jpg"),
                "https://s3.picacomic.com/static/",
            ),
        )
    }

    /**
     * 服务端在 `thumb.path` 里直接下发缩略图签名路径（`tobeimg/<token>/rs:fill:…/<b64>.jpg`），
     * 客户端只拼 `fileServer + "/static/" + path`，不参与签名。
     */
    @Test
    fun `服务端下发的 tobeimg 缩略图路径原样拼接`() {
        val path = "tobeimg/ojaW_ArrKzG8bf3gN7mPfVnRhHNiCcapjEYv17ZIDxk" +
            "/rs:fill:300:400:0/g:sm/aHR0cHM6Ly9zdG9yYWdlLWIuanBn.jpg"
        assertEquals(
            "https://storage-b.picacomic.com/static/$path",
            PicaImage.url(thumb("https://storage-b.picacomic.com", path)),
        )
        assertEquals(
            "https://img.example.com/static/$path",
            PicaImage.url(thumb("https://storage-b.picacomic.com", path), "https://img.example.com"),
        )
    }

    @Test
    fun `缺字段返回 null`() {
        assertNull(PicaImage.url(null))
        assertNull(PicaImage.url(thumb("", "a.jpg")))
        assertNull(PicaImage.url(thumb("https://storage1.picacomic.com", "")))
    }
}
