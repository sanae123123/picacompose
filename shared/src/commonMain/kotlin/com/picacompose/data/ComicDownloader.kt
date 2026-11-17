package com.picacompose.data

import com.picacompose.picaapi.PicaException
import com.picacompose.picaapi.PicaImage
import com.picacompose.picaapi.PicaImageException
import com.picacompose.picaapi.model.Episode
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.time.TimeSource

/**
 * 本子缓存：把整本的图下下来，打包成一个 zip 放到 APP 私有目录。
 *
 * 目录长这样（`files/downloads`）：
 * - `{comicId}.zip`：下好的包。根目录 `metadata.json`，图片在 `image/` 下。
 * - `{comicId}.meta.json`：和 zip 里同一份元数据，给详情页/阅读器快速查询，不打开 zip。
 * - `work/{comicId}/task.json`：任务清单（有哪些话）。
 * - `work/{comicId}/plan/{order}.json`：每话的图片列表，拉过一次就缓存着，续下不用重拉。
 * - `work/{comicId}/data/image/{order}_{index}.{ext}`：下好的图。
 *
 * 中断恢复全靠目录本身：
 * - 每张图先写 `.part` 再改名，半截文件不会被当成下好了；
 * - 清单文件都原子写；
 * - 打包先写 `.zip.tmp`，打完再改名 —— `{comicId}.zip` 要么没有、要么完整；
 * - 重启后扫 `work/` 里还有 `task.json` 的目录接着下，已存在的图直接跳过。
 *
 * 本子更新时按「增量」走：只下变了的话，旧包里没变的图原样搬进新包（不解压整包），
 * 最后原子替换。变太多或者包读不动才整本重来。
 */
object ComicDownloader {

    /** 单本里同时下几张图。 */
    private const val CONCURRENCY = 4

    /**
     * 所有本子共用的网络并发上限。
     *
     * 同时缓存几本的时候不是每本各开 4 个请求：加起来也就在这个数附近，
     * 免得多本一起把图床和手机流量打满。
     */
    private const val NETWORK_CONCURRENCY = 5

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

    /** 所有请求共用的名额：图片和「拉图片列表」都从这里拿。 */
    private val networkPermits = Semaphore(NETWORK_CONCURRENCY)

    /** 这次缓存一共下了多少字节（没有活跃任务时清零）。 */
    private val transferredBytes = MutableStateFlow(0L)

    /** 正在缓存的本子叫什么：通知栏要显示，不能只报一个 id。 */
    private val titles = MutableStateFlow<Map<String, String>>(emptyMap())

    private var speedAnchor = TimeSource.Monotonic.markNow()
    private var speedBytes = 0L
    private var speedPerSecond = 0L

    fun statusOf(comicId: String): DownloadStatus = _status.value[comicId] ?: DownloadStatus.Idle

    /**
     * 开始缓存一本。
     *
     * 已经在下的不会重复开；已经打包好的什么都不做（更新走 [sync]）；
     * 之前失败的会从头走一遍（已下好的图直接跳过）。
     */
    fun start(comicId: String, titleHint: String? = null) {
        if (!supported || comicId.isBlank()) return
        scope.launch {
            lock.withLock {
                if (jobs[comicId]?.isActive == true) return@withLock
                if (!titleHint.isNullOrBlank()) {
                    titles.update { it + (comicId to titleHint) }
                }
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
     * 检查已缓存的本子有没有更新：详情页拉完元数据后调一次。
     *
     * 每次进详情页都要拉一遍章节目录（服务端为准），和本地包里的元数据对一下：
     * 一致就什么都不做；只有几话变了就补那几话；变太多、或者包读不动，整本重来。
     */
    fun sync(comicId: String) {
        if (!supported || comicId.isBlank()) return
        scope.launch {
            lock.withLock {
                if (jobs[comicId]?.isActive == true) return@withLock
                if (!store.exists(zipPath(comicId))) return@withLock
                jobs[comicId] = scope.launch { runSync(comicId) }
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

    // ---------------------------------------------------------------- 下载任务

    private suspend fun runTask(comicId: String, titleHint: String?) {
        try {
            runTaskBody(comicId, titleHint)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setStatus(comicId, DownloadStatus.Failed(e.message ?: "下载失败"))
        } finally {
            lock.withLock { jobs.remove(comicId) }
        }
    }

    /** 从零（或断点）把整本下完、打包。 */
    private suspend fun runTaskBody(comicId: String, titleHint: String?) {
        setStatus(comicId, DownloadStatus.Preparing)
        val task = loadTask(comicId, titleHint)
        titles.update { it + (comicId to task.title) }
        val total = prepareChapters(comicId, task.chapters)
        setStatus(comicId, DownloadStatus.Running(0, total))
        downloadPages(comicId, task.chapters, total)
        pack(comicId, task)
        val zip = zipPath(comicId)
        store.deleteTree(workDir(comicId))
        setStatus(comicId, DownloadStatus.Completed(store.size(zip)))
    }

    private suspend fun runSync(comicId: String) {
        try {
            val zip = zipPath(comicId)
            if (!store.exists(zip)) return
            val local = OfflineLibrary.meta(comicId)
            val remote = fetchEpisodes(comicId)
            if (remote.isEmpty()) return
            if (local == null) {
                // 没有元数据就没法对账（比如包是从别处拷来的），只能整本重来。
                fullUpdate(comicId)
                return
            }
            val localOrders = local.chapters.associateBy { it.order }
            val changed = sortedSetOf<Int>()
            for (ep in remote) {
                val old = localOrders[ep.order]
                if (old == null) {
                    changed += ep.order
                    continue
                }
                if (old.title != ep.title) {
                    changed += ep.order
                    continue
                }
                val touched = when {
                    old.updatedAt == null && ep.updatedAt != null -> true
                    old.updatedAt != null && ep.updatedAt != null && old.updatedAt != ep.updatedAt -> true
                    else -> false
                }
                if (touched) changed += ep.order
            }
            val removed = localOrders.keys - remote.map { it.order }.toSet()
            val touched = changed + removed
            if (touched.isEmpty()) {
                setStatus(comicId, DownloadStatus.Completed(store.size(zip)))
                return
            }
            // 变了一小半以上就不折腾了：整本重下比一层层补更稳。
            val tooMany = touched.size > (localOrders.size / 2).coerceAtLeast(2)
            if (tooMany) {
                fullUpdate(comicId)
                return
            }
            incremental(comicId, local, remote, changed, removed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setStatus(comicId, DownloadStatus.Failed(e.message ?: "更新失败"))
        } finally {
            lock.withLock { jobs.remove(comicId) }
        }
    }

    /** 整本重来：临时目录清掉，zip 原地重下（打包成功才覆盖旧包）。 */
    private suspend fun fullUpdate(comicId: String) {
        store.deleteTree(workDir(comicId))
        store.delete(OfflineLibrary.metaPath(comicId))
        OfflineLibrary.invalidate(comicId)
        runTaskBody(comicId, null)
    }

    /**
     * 增量更新：只下变过的话，没变的图从旧包原样搬过去。
     *
     * 整个过程不解压旧包：重写 zip 时把旧条目流式复制，再补上新图和新元数据，
     * 最后 `.tmp` 改名覆盖 —— 中途失败旧包还是完整的。
     */
    private suspend fun incremental(
        comicId: String,
        local: DownloadMeta,
        remote: List<Episode>,
        changed: Set<Int>,
        removed: Set<Int>,
    ) {
        setStatus(comicId, DownloadStatus.Preparing)
        titles.update { it + (comicId to local.title) }
        val dir = workDir(comicId)
        // 上次中断留下的临时目录先清掉：增量只是补变化的话，重来一遍最省心。
        store.deleteTree(dir)
        store.mkdirs("$dir/plan")
        store.mkdirs(imageDir(comicId))

        val changedChapters = remote
            .filter { it.order in changed }
            .sortedBy { it.order }
            .map { ChapterPlan(order = it.order, title = it.title, updatedAt = it.updatedAt) }
        val total = prepareChapters(comicId, changedChapters)
        setStatus(comicId, DownloadStatus.Running(0, total))
        downloadPages(comicId, changedChapters, total)

        // 新元数据：没变的话沿用旧页数，变了的用刚拉到的 plan。
        val chapters = remote.sortedBy { it.order }.map { ep ->
            DownloadedChapter(
                order = ep.order,
                title = ep.title,
                pages = readPlan(comicId, ep.order)?.pages?.size
                    ?: local.chapters.firstOrNull { it.order == ep.order }?.pages
                    ?: 0,
                updatedAt = ep.updatedAt,
            )
        }
        val meta = DownloadMeta(
            comicId = comicId,
            title = local.title,
            author = local.author,
            cover = local.cover,
            downloadedAt = pica.serverNowEpochSeconds() * 1000L,
            chapters = chapters,
            pages = chapters.sumOf { it.pages },
        )
        store.writeTextAtomic(metaPath(comicId), json.encodeToString(DownloadMeta.serializer(), meta))

        val drop = changed + removed
        val rewritten = store.rewriteZip(
            srcZip = zipPath(comicId),
            keep = { name -> keepEntry(name, drop) },
            appendDir = dataDir(comicId),
        )
        if (!rewritten) {
            // 旧包读不动（很少见）：整本重来，别留一个半新半旧的东西。
            fullUpdate(comicId)
            return
        }
        writeSidecar(comicId, meta)
        store.deleteTree(workDir(comicId))
        setStatus(comicId, DownloadStatus.Completed(store.size(zipPath(comicId))))
    }

    /** 旧包里哪些条目留着：`metadata.json` 换成新的，变过的话整话的图都换掉。 */
    private fun keepEntry(name: String, drop: Set<Int>): Boolean {
        if (name == "metadata.json") return false
        if (!name.startsWith("image/")) return false
        val order = name.removePrefix("image/").substringBefore('_').toIntOrNull() ?: return false
        return order !in drop
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
        val chapters = fetchEpisodes(comicId).map {
            ChapterPlan(order = it.order, title = it.title, updatedAt = it.updatedAt)
        }

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

    private suspend fun fetchEpisodes(comicId: String): List<Episode> {
        val all = mutableListOf<Episode>()
        var page = 1
        while (true) {
            val paged = withNetworkRetry { pica.comics.episodes(comicId, page) }
            all += paged.docs.filter { it.order > 0 }
            if (paged.docs.isEmpty() || (paged.pages > 0 && page >= paged.pages)) break
            page++
        }
        return all.distinctBy { it.order }.sortedBy { it.order }
    }

    // ---------------------------------------------------------------- 图片列表

    /** 每话的图片列表拉一遍（有缓存就读），返回总页数。 */
    private suspend fun prepareChapters(comicId: String, chapters: List<ChapterPlan>): Int = coroutineScope {
        var total = 0
        chapters.chunked(PREPARE_CONCURRENCY).forEach { chunk ->
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
    private suspend fun downloadPages(comicId: String, chapters: List<ChapterPlan>, total: Int) {
        val done = MutableStateFlow(0)
        val items = mutableListOf<PlanItem>()
        for (chapter in chapters.sortedBy { it.order }) {
            val plan = readPlan(comicId, chapter.order) ?: continue
            plan.pages.forEachIndexed { index, page ->
                items += PlanItem(chapter.order, index, page)
            }
        }
        coroutineScope {
            items.chunked(CONCURRENCY).forEach { chunk ->
                chunk.map { item ->
                    async {
                        // 上次已经下好的直接算数，不用再看网络。
                        if (findImageFile(comicId, item.order, item.index) == null) {
                            downloadPage(comicId, item)
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
    private suspend fun downloadPage(comicId: String, item: PlanItem) {
        val thumb = Thumbnail(fileServer = item.page.fileServer, path = item.page.path)
        val url = pica.imageUrl(thumb) ?: throw IllegalStateException("图片地址拼不出来")
        val candidates = (listOf(url) + alternateImageUrls(url)).distinct()
        var attempt = 0
        while (true) {
            var retryable = false
            var lastError: Exception? = null
            for (candidate in candidates) {
                try {
                    val bytes = networkPermits.withPermit { pica.fetchImageBytes(candidate) }
                    val ext = imageKind(bytes).ext
                    store.writeBytesAtomic(imagePath(comicId, item.order, item.index, ext), bytes)
                    cleanOldVariants(comicId, item.order, item.index, ext)
                    transferredBytes.updateAndGet { it + bytes.size }
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

    /** 本地这页已经下好的文件（扩展名按内容定过，挨个试）。 */
    private fun findImageFile(comicId: String, order: Int, index: Int): String? {
        for (ext in IMAGE_EXTENSIONS) {
            val path = imagePath(comicId, order, index, ext)
            if (store.exists(path) && store.size(path) > 0) return path
        }
        return null
    }

    /** 之前按别的后缀存过同一页的话顺手删掉，免得 zip 里同名双份。 */
    private fun cleanOldVariants(comicId: String, order: Int, index: Int, keepExt: String) {
        for (ext in IMAGE_EXTENSIONS) {
            if (ext == keepExt) continue
            store.delete(imagePath(comicId, order, index, ext))
        }
    }

    // ---------------------------------------------------------------- 打包

    /** 写元数据、打 zip、把临时目录删掉。 */
    private suspend fun pack(comicId: String, task: DownloadTask) {
        val chapters = task.chapters.sortedBy { it.order }.map { chapter ->
            DownloadedChapter(
                order = chapter.order,
                title = chapter.title,
                pages = readPlan(comicId, chapter.order)?.pages?.size ?: 0,
                updatedAt = chapter.updatedAt,
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
        writeSidecar(comicId, meta)
    }

    /** 往下载目录放一份元数据副本：阅读器/详情页查缓存不打开 zip。 */
    private fun writeSidecar(comicId: String, meta: DownloadMeta) {
        runCatching {
            store.writeTextAtomic(
                OfflineLibrary.metaPath(comicId),
                json.encodeToString(DownloadMeta.serializer(), meta),
            )
        }
        OfflineLibrary.invalidate(comicId)
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
                return networkPermits.withPermit { block() }
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
        refreshNotice()
    }

    /** 通知栏跟着进度走：只在有活干的时候挂出来，全下完就撤掉。 */
    private fun refreshNotice() {
        if (!DownloadNotifier.supported) return
        val statuses = _status.value
        val names = titles.value
        val items = statuses.mapNotNull { (id, status) ->
            when (status) {
                is DownloadStatus.Running -> DownloadNoticeItem(
                    title = names[id].orEmpty(),
                    done = status.done,
                    total = status.total,
                )
                is DownloadStatus.Preparing -> DownloadNoticeItem(
                    title = names[id].orEmpty(),
                    done = 0,
                    total = 0,
                )
                else -> null
            }
        }
        if (items.isEmpty()) {
            // 全部收工：通知撤掉，把它攒的数字清零，下一轮从头算。
            transferredBytes.value = 0
            speedBytes = 0
            speedPerSecond = 0
            speedAnchor = TimeSource.Monotonic.markNow()
            DownloadNotifier.update(null)
            return
        }
        val now = TimeSource.Monotonic.markNow()
        val elapsed = (now - speedAnchor).inWholeMilliseconds
        if (elapsed >= 800) {
            val current = transferredBytes.value
            speedPerSecond = ((current - speedBytes) * 1000 / elapsed).coerceAtLeast(0)
            speedBytes = current
            speedAnchor = now
        }
        var done = 0
        var total = 0
        for (status in statuses.values) {
            if (status is DownloadStatus.Running) {
                done += status.done
                total += status.total
            }
        }
        DownloadNotifier.update(
            DownloadNotice(
                items = items,
                done = done,
                total = total,
                bytes = transferredBytes.value,
                bytesPerSecond = speedPerSecond,
            ),
        )
    }

    /**
     * 下载管理页要的列表：下好的包 + 还没打包的任务。
     *
     * 扫目录、读几份 sidecar，量很小；放后台线程跑，别在主线程碰文件。
     */
    suspend fun downloads(): List<DownloadedBook> = withContext(Dispatchers.Default) {
        val root = store.root
        if (root.isBlank()) return@withContext emptyList()
        val entries = mutableListOf<DownloadedBook>()
        val zips = runCatching { store.list(root).filter { it.endsWith(".zip") } }
            .getOrDefault(emptyList())
        for (name in zips) {
            val id = name.removeSuffix(".zip")
            if (id.isBlank()) continue
            val meta = OfflineLibrary.meta(id)
            val bytes = store.size("$root/$name")
            val status = statusOf(id).let {
                if (it is DownloadStatus.Idle) DownloadStatus.Completed(bytes) else it
            }
            entries += DownloadedBook(
                comicId = id,
                title = meta?.title?.ifBlank { id } ?: id,
                cover = meta?.cover,
                pages = meta?.pages ?: 0,
                bytes = bytes,
                status = status,
            )
        }
        val workRoot = "$root/work"
        runCatching { store.list(workRoot) }.getOrDefault(emptyList()).forEach { id ->
            if (zips.any { it == "$id.zip" }) return@forEach
            val status = statusOf(id)
            if (status is DownloadStatus.Idle) return@forEach
            val task = store.readText("$workRoot/$id/task.json")?.let { raw ->
                runCatching { json.decodeFromString(DownloadTask.serializer(), raw) }.getOrNull()
            }
            entries += DownloadedBook(
                comicId = id,
                title = task?.title?.ifBlank { id } ?: id,
                cover = task?.cover,
                pages = (status as? DownloadStatus.Running)?.total ?: 0,
                bytes = 0L,
                status = status,
            )
        }
        entries.sortedWith(
            compareBy<DownloadedBook> { it.status is DownloadStatus.Completed }.thenBy { it.title },
        )
    }

    /** 取消正在下的这一本：图留在 work 里，下次点缓存接着下。 */
    fun cancel(comicId: String) {
        if (!supported) return
        scope.launch {
            lock.withLock {
                jobs.remove(comicId)?.cancel()
                _status.update { it - comicId }
                titles.update { it - comicId }
                refreshNotice()
            }
        }
    }

    /** 删掉一本的整份缓存：进行中的先停，再把 zip / 元数据 / 临时文件都清掉。 */
    fun delete(comicId: String) {
        if (!supported) return
        scope.launch {
            lock.withLock {
                jobs.remove(comicId)?.cancel()
                titles.update { it - comicId }
                store.delete(zipPath(comicId))
                store.delete(OfflineLibrary.metaPath(comicId))
                store.deleteTree(workDir(comicId))
                OfflineLibrary.invalidate(comicId)
                _status.update { it - comicId }
                refreshNotice()
            }
        }
    }

    /** 导出用：缓存包的路径；还没打包好就 null。 */
    internal fun cachedZipPath(comicId: String): String? {
        val path = zipPath(comicId)
        return if (runCatching { store.exists(path) }.getOrDefault(false)) path else null
    }

    private fun workDir(comicId: String) = "${store.root}/work/$comicId"

    private fun dataDir(comicId: String) = "${workDir(comicId)}/data"

    private fun imageDir(comicId: String) = "${dataDir(comicId)}/image"

    private fun taskPath(comicId: String) = "${workDir(comicId)}/task.json"

    private fun planPath(comicId: String, order: Int) = "${workDir(comicId)}/plan/$order.json"

    private fun metaPath(comicId: String) = "${dataDir(comicId)}/metadata.json"

    private fun zipPath(comicId: String) = "${store.root}/$comicId.zip"

    private fun imagePath(comicId: String, order: Int, index: Int, ext: String) =
        "${imageDir(comicId)}/${order}_$index.$ext"
}
