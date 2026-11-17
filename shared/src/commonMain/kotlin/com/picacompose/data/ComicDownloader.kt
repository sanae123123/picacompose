package com.picacompose.data

import com.picacompose.picaapi.PicaException
import com.picacompose.picaapi.PicaImage
import com.picacompose.picaapi.PicaImageException
import com.picacompose.picaapi.model.Thumbnail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * 本子缓存：把整本的图下下来，打包成一个 zip 放到 APP 私有目录。
 *
 * 目录长这样（`files/downloads`）：
 * - `{comicId}.zip`：下好的包。根目录 `metadata.json`，图片在 `image/` 下。
 * - `work/{comicId}/task.json`：任务清单（有哪些话）。
 * - `work/{comicId}/plan/{order}.json`：每话的图片列表，拉过一次就缓存着，续下不用重拉。
 * - `work/{comicId}/data/image/{order}_{index}.{ext}`：下好的图。
 * - `work/{comicId}/data/metadata.json`：打包前的元数据。
 *
 * 中断恢复全靠目录本身：
 * - 每张图先写 `.part` 再改名，半截文件不会被当成下好了；
 * - 清单文件都原子写；
 * - 打包先写 `.zip.tmp`，打完再改名 —— `{comicId}.zip` 要么没有、要么完整；
 * - 重启后扫 `work/` 里还有 `task.json` 的目录接着下，已存在的图直接跳过。
 *
 * 整本放在一个 zip 里（而不是每话一个），是照着「本子」的粒度来的：
 * 分享、导出、删除都是一个文件，元数据还能带着标题和话数。
 */
object ComicDownloader {

    /** 同时下几张图。手机流量/图床并发都扛得住，再多也快不了多少。 */
    private const val CONCURRENCY = 4

    /** 一次准备阶段（拉每话图片列表）同时请求几话。 */
    private const val PREPARE_CONCURRENCY = 4

    /** 同一个请求最多试这么多次；断网太久就先停下，等下次重开 App 接着来。 */
    private const val MAX_ATTEMPTS = 30

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val store: DownloadStore by lazy { platformDownloadStore() }

    /** 当前平台支不支持缓存（iOS 暂时没有）。 */
    val supported: Boolean
        get() = runCatching { store.supported }.getOrDefault(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _status = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())

    /** 所有本子的缓存状态：详情页按 comicId 取。 */
    val status: StateFlow<Map<String, DownloadStatus>> = _status.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()
    private val lock = Mutex()
    private var resumed = false

    fun statusOf(comicId: String): DownloadStatus = _status.value[comicId] ?: DownloadStatus.Idle

    /**
     * 开始缓存一本。
     *
     * 已经在下的不会重复开；已经打包好的什么都不做；之前失败的会从头走一遍（已下好的图直接跳过）。
     */
    fun start(comicId: String, titleHint: String? = null) {
        if (!supported || comicId.isBlank()) return
        scope.launch {
            lock.withLock {
                if (jobs[comicId]?.isActive == true) return@withLock
                val zip = zipPath(comicId)
                if (store.exists(zip)) {
                    setStatus(comicId, DownloadStatus.Completed(store.size(zip)))
                    return@withLock
                }
                jobs[comicId] = scope.launch { runTask(comicId, titleHint) }
            }
        }
    }

    /**
     * App 启动后调一次：把上次没下完的接着下。
     *
     * 已经打包好但状态还没摆出来的本子也顺手补上，详情页按钮才能显示「已缓存」。
     */
    fun resumePending() {
        if (!supported) return
        scope.launch {
            lock.withLock {
                if (resumed) return@withLock
                resumed = true
                runCatching {
                    val root = store.root
                    store.mkdirs("$root/work")
                    store.list(root).filter { it.endsWith(".zip") }.forEach { name ->
                        val id = name.removeSuffix(".zip")
                        if (id.isNotBlank()) {
                            setStatus(id, DownloadStatus.Completed(store.size("$root/$name")))
                        }
                    }
                    store.list("$root/work").forEach { id ->
                        val dir = "$root/work/$id"
                        if (!store.exists("$dir/task.json")) {
                            // 清单都没写下来就断了（刚开个头）：目录留着没用，清掉。
                            store.deleteTree(dir)
                            return@forEach
                        }
                        if (jobs[id]?.isActive == true) return@forEach
                        jobs[id] = scope.launch { runTask(id, null) }
                    }
                }
            }
        }
    }

    private suspend fun runTask(comicId: String, titleHint: String?) {
        try {
            setStatus(comicId, DownloadStatus.Preparing)
            val task = loadTask(comicId, titleHint)
            val total = preparePlans(comicId, task)
            setStatus(comicId, DownloadStatus.Running(0, total))
            downloadPages(comicId, task, total)
            pack(comicId, task)
            val zip = zipPath(comicId)
            store.deleteTree(workDir(comicId))
            setStatus(comicId, DownloadStatus.Completed(store.size(zip)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setStatus(comicId, DownloadStatus.Failed(e.message ?: "下载失败"))
        } finally {
            lock.withLock { jobs.remove(comicId) }
        }
    }

    // ---------------------------------------------------------------- 任务清单

    /** 读清单；没有就拉详情和章节目录，写一份下来。 */
    private suspend fun loadTask(comicId: String, titleHint: String?): DownloadTask {
        val cached = store.readText(taskPath(comicId))?.let { raw ->
            runCatching { json.decodeFromString(DownloadTask.serializer(), raw) }.getOrNull()
        }
        if (cached != null && cached.chapters.isNotEmpty()) return cached

        val dir = workDir(comicId)
        store.mkdirs("$dir/plan")
        store.mkdirs(imageDir(comicId))

        val detail = withNetworkRetry { pica.comics.detail(comicId) }
        val episodes = mutableListOf<com.picacompose.picaapi.model.Episode>()
        var page = 1
        while (true) {
            val paged = withNetworkRetry { pica.comics.episodes(comicId, page) }
            episodes += paged.docs.filter { it.order > 0 }
            if (paged.docs.isEmpty() || (paged.pages > 0 && page >= paged.pages)) break
            page++
        }
        val chapters = episodes
            .distinctBy { it.order }
            .sortedBy { it.order }
            .map { ChapterPlan(order = it.order, title = it.title) }

        val task = DownloadTask(
            comicId = comicId,
            title = detail.title.ifBlank { titleHint.orEmpty().ifBlank { "本子" } },
            author = detail.author,
            cover = pica.imageUrl(detail.thumb),
            createdAt = pica.serverNowEpochSeconds() * 1000L,
            chapters = chapters,
        )
        store.writeTextAtomic(taskPath(comicId), json.encodeToString(DownloadTask.serializer(), task))
        return task
    }

    // ---------------------------------------------------------------- 图片列表

    /** 每话的图片列表拉一遍（有缓存就读），返回总页数。 */
    private suspend fun preparePlans(comicId: String, task: DownloadTask): Int = coroutineScope {
        var total = 0
        task.chapters.chunked(PREPARE_CONCURRENCY).forEach { chunk ->
            val counts = chunk.map { chapter ->
                async { prepareChapter(comicId, chapter) }
            }.awaitAll()
            total += counts.sum()
        }
        total
    }

    private suspend fun prepareChapter(comicId: String, chapter: ChapterPlan): Int {
        val path = planPath(comicId, chapter.order)
        store.readText(path)?.let { raw ->
            runCatching { json.decodeFromString(ChapterPlan.serializer(), raw) }.getOrNull()
        }?.takeIf { it.pages.isNotEmpty() }?.let { return it.pages.size }

        val pages = mutableListOf<PagePlan>()
        var page = 1
        while (true) {
            val data = withNetworkRetry { pica.comics.pages(comicId, chapter.order, page) }
            val docs = data.pages.docs
            pages += docs.map { PagePlan(fileServer = it.media?.fileServer, path = it.media?.path) }
            if (docs.isEmpty() || (data.pages.pages > 0 && page >= data.pages.pages)) break
            page++
        }
        val plan = chapter.copy(pages = pages)
        store.writeTextAtomic(path, json.encodeToString(ChapterPlan.serializer(), plan))
        return pages.size
    }

    // ---------------------------------------------------------------- 下图片

    /** 把还缺的页下完，每成一张报一次进度，详情页的进度环跟着走。 */
    private suspend fun downloadPages(comicId: String, task: DownloadTask, total: Int) {
        val done = MutableStateFlow(0)
        val items = mutableListOf<PlanItem>()
        for (chapter in task.chapters.sortedBy { it.order }) {
            val plan = readPlan(comicId, chapter.order) ?: continue
            plan.pages.forEachIndexed { index, page ->
                items += PlanItem(chapter.order, index, page)
            }
        }
        coroutineScope {
            items.chunked(CONCURRENCY).forEach { chunk ->
                chunk.map { item ->
                    async {
                        val file = imagePath(comicId, item.order, item.index, item.page.path)
                        // 上次已经下好的直接算数，不用再看网络。
                        if (!store.exists(file) || store.size(file) <= 0) {
                            downloadPage(item.page, file)
                        }
                        setStatus(comicId, DownloadStatus.Running(done.updateAndGet { it + 1 }, total))
                    }
                }.awaitAll()
            }
        }
    }

    /** 一页要下的东西：第几话、第几页（下标）、图床坐标。 */
    private data class PlanItem(val order: Int, val index: Int, val page: PagePlan)

    /**
     * 下一张图：主图床失败就换备用图床，网络类错误退避重试。
     *
     * 4xx（429 除外）说明这张图本来就不在，换地址再试也没用，直接抛给任务层。
     */
    private suspend fun downloadPage(page: PagePlan, target: String) {
        val thumb = Thumbnail(fileServer = page.fileServer, path = page.path)
        val url = pica.imageUrl(thumb) ?: throw IllegalStateException("图片地址拼不出来")
        val candidates = (listOf(url) + alternateImageUrls(url)).distinct()
        var attempt = 0
        while (true) {
            var retryable = false
            var lastError: Exception? = null
            for (candidate in candidates) {
                try {
                    val bytes = pica.fetchImageBytes(candidate)
                    store.writeBytesAtomic(target, bytes)
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (e: PicaImageException) {
                    lastError = e
                    if (!e.permanent) retryable = true
                } catch (e: Exception) {
                    lastError = e
                    retryable = true
                }
            }
            if (!retryable) throw lastError ?: IllegalStateException("图片下载失败")
            attempt++
            if (attempt >= MAX_ATTEMPTS) throw lastError ?: IllegalStateException("网络一直连不上")
            delay(backoff(attempt))
        }
    }

    // ---------------------------------------------------------------- 打包

    /** 写元数据、打 zip、把临时目录删掉。 */
    private suspend fun pack(comicId: String, task: DownloadTask) {
        val chapters = task.chapters.map { chapter ->
            val plan = readPlan(comicId, chapter.order)
            DownloadedChapter(
                order = chapter.order,
                title = chapter.title,
                pages = plan?.pages?.size ?: 0,
            )
        }
        val meta = DownloadMeta(
            comicId = comicId,
            title = task.title,
            author = task.author,
            cover = task.cover,
            downloadedAt = pica.serverNowEpochSeconds() * 1000L,
            chapters = chapters,
            pages = chapters.sumOf { it.pages },
        )
        store.writeTextAtomic(metaPath(comicId), json.encodeToString(DownloadMeta.serializer(), meta))
        if (!store.zipDir(dataDir(comicId), zipPath(comicId))) {
            throw IllegalStateException("打包失败，磁盘可能满了")
        }
    }

    // ---------------------------------------------------------------- 小工具

    private fun readPlan(comicId: String, order: Int): ChapterPlan? =
        store.readText(planPath(comicId, order))?.let { raw ->
            runCatching { json.decodeFromString(ChapterPlan.serializer(), raw) }.getOrNull()
        }

    /** 网络类错误一直退避重试；明确的 4xx 直接抛，别拿「图本来就没有」反复磨。 */
    private suspend fun <T> withNetworkRetry(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: PicaException) {
                val code = e.httpCode
                if (code != null && code in 400..499 && code != 429) throw e
                attempt++
                if (attempt >= MAX_ATTEMPTS) throw e
                delay(backoff(attempt))
            } catch (e: Exception) {
                attempt++
                if (attempt >= MAX_ATTEMPTS) throw e
                delay(backoff(attempt))
            }
        }
    }

    /** 1s、2s、4s…… 最长 60 秒一次：断网时别一直砸请求，也别等太久。 */
    private fun backoff(attempt: Int): Long =
        (1_000L shl attempt.coerceIn(0, 6)).coerceAtMost(60_000L)

    private fun setStatus(comicId: String, value: DownloadStatus) {
        _status.update { it + (comicId to value) }
    }

    private fun workDir(comicId: String) = "${store.root}/work/$comicId"

    private fun dataDir(comicId: String) = "${workDir(comicId)}/data"

    private fun imageDir(comicId: String) = "${dataDir(comicId)}/image"

    private fun taskPath(comicId: String) = "${workDir(comicId)}/task.json"

    private fun planPath(comicId: String, order: Int) = "${workDir(comicId)}/plan/$order.json"

    private fun metaPath(comicId: String) = "${dataDir(comicId)}/metadata.json"

    private fun zipPath(comicId: String) = "${store.root}/$comicId.zip"

    private fun imagePath(comicId: String, order: Int, index: Int, path: String?) =
        "${imageDir(comicId)}/${order}_${index}.${extension(path)}"

    /** 按 path 后缀猜扩展名；猜不出来按 jpg 算（zip 里只是文件后缀，看图的工具按内容识别也行）。 */
    private fun extension(path: String?): String {
        val tail = path
            ?.substringBefore('?')
            ?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf { it in setOf("png", "jpg", "jpeg", "webp", "gif", "avif", "bmp") }
        return tail ?: "jpg"
    }
}
