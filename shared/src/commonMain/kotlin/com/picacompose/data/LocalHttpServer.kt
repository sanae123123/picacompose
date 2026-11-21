package com.picacompose.data

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.isClosed
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.sockets.port
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 一条 http 响应：状态码、类型、正文。正文按字节给，页面是文本也先编码好。 */
class LocalHttpResponse(
    val body: ByteArray,
    val contentType: String = "text/html; charset=utf-8",
    val status: Int = 200,
) {
    companion object {
        /** 最常见的那个：一整页 HTML。 */
        fun html(body: String): LocalHttpResponse = LocalHttpResponse(body.encodeToByteArray())

        /** 纯文本，调试或者返回错误说明用。 */
        fun text(body: String, status: Int = 200): LocalHttpResponse =
            LocalHttpResponse(body.encodeToByteArray(), "text/plain; charset=utf-8", status)
    }
}

/** 路径 → 响应。返回 null 就是 404，自己抛异常就是 500。 */
typealias LocalHttpHandler = suspend (path: String) -> LocalHttpResponse?

/**
 * 应用内用的小 http 服务：只在 `127.0.0.1` 上听，端口由系统分配。
 *
 * 为什么要它：应用里的 WebView 直接开 `file://` 会被各种限制挡住（fetch、模块、相对路径、
 * 跨目录访问），换成 `http://127.0.0.1:端口/...` 这些就都正常了，页面代码可以按普通网页写。
 * 之后的应用内帮助页就是这么用的。
 *
 * 生命周期跟界面走：界面起来时 [start]，退出时 [stop]，不常驻、不在后台跑。
 * 端口是随机分的，分完存在 [port] 里 —— 同进程里谁要拼地址就用 [url]，别自己写死。
 *
 * 只做了够用的一小撮：GET/HEAD 一行请求行 + 读完请求头就忽略、每个连接只答一条、答完就关。
 * 不需要分块传输、不支持 keep-alive、不解析 body；本地页面用不到这些。
 */
object LocalHttpServer {

    private val _port = MutableStateFlow<Int?>(null)

    /** 当前监听的端口；没起来时是 null。界面读它就知道该往哪个地址加载。 */
    val port: StateFlow<Int?> = _port.asStateFlow()

    private val lock = Mutex()
    private var selector: SelectorManager? = null
    private var server: ServerSocket? = null
    private var scope: CoroutineScope? = null
    private var acceptJob: Job? = null

    /**
     * 起来（已经在跑就直接沿用），返回监听端口。
     *
     * 绑 `0` 让系统挑空闲端口：写死端口会在别的进程占用时直接失败，而且应用内也不需要一个
     * 固定的号。端口拿到后记在 [port] 里，同一个进程里到处都能读到。
     */
    suspend fun start(handler: LocalHttpHandler): Int = lock.withLock {
        val running = server
        if (running != null && !running.isClosed) return _port.value ?: 0

        val selectorManager = SelectorManager(Dispatchers.IO)
        val socket = aSocket(selectorManager).tcp().bind(port = 0)
        val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        selector = selectorManager
        server = socket
        scope = jobScope
        _port.value = socket.port
        acceptJob = jobScope.launch {
            while (isActive) {
                // accept 在 socket 关掉之后会抛，那是正常退出路径，不该把协程炸出去。
                val client = runCatching { socket.accept() }.getOrNull() ?: continue
                launch { handle(client, handler) }
            }
        }
        return socket.port
    }

    /** 关掉：先停 accept 循环，再关 socket，别让已经进来的请求卡住。 */
    suspend fun stop() {
        lock.withLock {
            acceptJob?.cancel()
            acceptJob = null
            runCatching { server?.close() }
            server = null
            selector?.let { runCatching { it.close() } }
            selector = null
            scope?.cancel()
            scope = null
            _port.value = null
        }
    }

    /** 拼一个本机地址：`http://127.0.0.1:端口` + path。没起来时是 null。 */
    fun url(path: String = "/"): String? {
        val at = _port.value ?: return null
        return "http://127.0.0.1:$at" + if (path.startsWith("/")) path else "/$path"
    }

    private suspend fun handle(client: Socket, handler: LocalHttpHandler) {
        try {
            val input = client.openReadChannel()
            val requestLine = input.readUTF8Line() ?: return
            // 请求头读到空行为止，内容不看 —— 这里只服务 GET/HEAD。
            while (true) {
                val line = input.readUTF8Line() ?: break
                if (line.isEmpty()) break
            }
            val path = requestLine.split(' ').getOrNull(1)?.substringBefore('?') ?: "/"
            val response = runCatching { handler(path) }.getOrElse { error ->
                LocalHttpResponse.text("内部错误：${error.message ?: error::class.simpleName}", status = 500)
            }
            val bytes = render(response ?: notFound())
            client.openWriteChannel(autoFlush = true).writeFully(bytes)
        } catch (t: Throwable) {
            // 单个连接出问题不该影响服务本身：记一行就完，反正对方看到的只是连接断掉。
            println("LocalHttpServer: ${t::class.simpleName}: ${t.message}")
        } finally {
            runCatching { client.close() }
        }
    }

    private fun notFound(): LocalHttpResponse = LocalHttpResponse.text("404：这个地址没有内容", status = 404)

    private fun render(response: LocalHttpResponse): ByteArray {
        val status = response.status
        val reason = when (status) {
            200 -> "OK"
            404 -> "Not Found"
            500 -> "Internal Server Error"
            else -> "OK"
        }
        val head = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: ${response.contentType}\r\n")
            append("Content-Length: ${response.body.size}\r\n")
            // 帮助页是随应用走的，不该被 WebView 缓存成旧版本。
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        return head.encodeToByteArray() + response.body
    }
}
