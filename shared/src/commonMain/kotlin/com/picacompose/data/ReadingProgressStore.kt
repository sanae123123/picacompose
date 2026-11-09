package com.picacompose.data

/** 一本本子读到哪儿了：第幾話、第几页（都从 1 / 0 数，page 是列表下标）。 */
data class ReadingProgress(
    val comicId: String,
    val order: Int,
    val page: Int,
    val pages: Int = 0,
    val title: String? = null,
)

/**
 * 阅读进度的持久化：一本一条，键是 `read.<comicId>`。
 *
 * 另外维护一个索引 `read.index`（逗号分隔的本子 id，最近读的排在最后），
 * 以后做「最近閱讀」直接拿 [all] 就行。存储和登录状态是同一套 [KeyValueStore]。
 */
class ReadingProgressStore(private val kv: KeyValueStore = platformKeyValueStore()) {

    fun load(comicId: String): ReadingProgress? {
        val raw = kv.get(key(comicId)) ?: return null
        return decode(comicId, raw)
    }

    fun save(progress: ReadingProgress) {
        kv.put(key(progress.comicId), encode(progress))
        val ids = index().filter { it != progress.comicId } + progress.comicId
        kv.put(KEY_INDEX, ids.joinToString(","))
    }

    /** 最近读的排前面。 */
    fun all(): List<ReadingProgress> =
        index().asReversed().mapNotNull { load(it) }

    fun clear(comicId: String) {
        kv.put(key(comicId), null)
        kv.put(KEY_INDEX, index().filter { it != comicId }.joinToString(","))
    }

    private fun index(): List<String> =
        kv.get(KEY_INDEX)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    private fun key(comicId: String) = "read.$comicId"

    // 标题里可能有制表符/换行，压成空格，字段之间用 \t 隔开。
    private fun encode(progress: ReadingProgress): String {
        val title = progress.title.orEmpty().replace('\t', ' ').replace('\n', ' ')
        return listOf(progress.order, progress.page, progress.pages, title).joinToString("\t")
    }

    private fun decode(comicId: String, raw: String): ReadingProgress? {
        val parts = raw.split('\t')
        if (parts.size < 3) return null
        return ReadingProgress(
            comicId = comicId,
            order = parts[0].toIntOrNull() ?: return null,
            page = parts[1].toIntOrNull() ?: 0,
            pages = parts[2].toIntOrNull() ?: 0,
            title = parts.getOrNull(3)?.takeIf { it.isNotBlank() },
        )
    }

    private companion object {
        const val KEY_INDEX = "read.index"
    }
}

/** 全 App 共用一个：每行列表都要查一眼，别每次都新建一个。 */
val readingProgressStore: ReadingProgressStore by lazy { ReadingProgressStore() }
