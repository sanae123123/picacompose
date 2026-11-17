package com.picacompose.data

import com.picacompose.picaapi.model.ComicPage
import com.picacompose.picaapi.model.Paged
import kotlinx.serialization.json.Json

/**
 * 离线包索引：哪本下好了、每话几页、第几页能直接从 zip 里拿。
 *
 * 打包完会往下载目录顺手写一份 sidecar（`{comicId}.meta.json`），和 zip 里的
 * `metadata.json` 是同一份。详情页、阅读器每次都要问「这本有没有缓存」，
 * 读一个小文件就够了 —— 不去解包，也不每次打开 zip。
 *
 * 图片本身不落盘：阅读器按 [OfflineImageKey] 让 Coil 直接从 zip 里取。
 */
internal object OfflineLibrary {

    private val json = Json { ignoreUnknownKeys = true }
    private val store: DownloadStore by lazy { platformDownloadStore() }

    private var cachedComicId: String? = null
    private var cachedMeta: DownloadMeta? = null

    fun meta(comicId: String): DownloadMeta? {
        if (comicId == cachedComicId) return cachedMeta
        var raw = runCatching { store.readText(metaPath(comicId)) }.getOrNull()
        if (raw == null) {
            // 上一版下的包没有 sidecar：从 zip 里那份读出来补上，省得整本重下。
            raw = offlineMetaFromZip(comicId)
            if (raw != null) runCatching { store.writeTextAtomic(metaPath(comicId), raw) }
        }
        val parsed = raw?.let {
            runCatching { json.decodeFromString(DownloadMeta.serializer(), it) }.getOrNull()
        }
        cachedComicId = comicId
        cachedMeta = parsed
        return parsed
    }

    fun invalidate(comicId: String) {
        if (cachedComicId == comicId) {
            cachedComicId = null
            cachedMeta = null
        }
    }

    /** 这一话在本地有几页，没有缓存返回 0。 */
    fun pageCount(comicId: String, order: Int): Int =
        meta(comicId)?.chapters?.firstOrNull { it.order == order }?.pages ?: 0

    /** 这一页在本地包里有没有。 */
    fun hasPage(comicId: String, order: Int, index: Int): Boolean {
        if (index < 0) return false
        return index < pageCount(comicId, order)
    }

    /**
     * 离线兜底：按本地元数据拼出这一话的页列表。
     *
     * 接口拉不到（没网、图床挂了）时阅读器还能翻已经下好的部分。
     * 只有第一页有内容，分页接口本来也是一话一页。
     */
    fun localPage(comicId: String, order: Int, page: Int): Paged<ComicPage>? {
        if (page != 1) return null
        val chapter = meta(comicId)?.chapters?.firstOrNull { it.order == order } ?: return null
        if (chapter.pages <= 0) return null
        val docs = (0 until chapter.pages).map { index ->
            ComicPage(id = "offline_${order}_$index")
        }
        return Paged(total = docs.size, limit = docs.size, page = 1, pages = 1, docs = docs)
    }

    fun metaPath(comicId: String): String = "${store.root}/$comicId.meta.json"
}
