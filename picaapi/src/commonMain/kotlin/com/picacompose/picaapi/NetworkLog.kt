package com.picacompose.picaapi

/**
 * 最近几次「连到哪台」的记录，设置里的网络链路页照着列。
 *
 * 只在新建连接（`Dns.lookup`）那一刻记一条：连接复用不会再记，所以一条就代表一次真连出去。
 * 用途由调用方标（图片 / 接口 / DoH），地址来源从 [ImageIps] 现查 ——
 * 一行长这样：`[图片] 104.19.53.76 [优选]`。
 */
object NetworkLog {

    /** 一条记录：用途、目标地址、地址是从哪一层来的。 */
    data class Entry(val purpose: String, val ip: String, val source: String) {
        override fun toString(): String = "[$purpose] $ip [$source]"
    }

    /** 留最近多少条：够看出这几分钟在连哪儿，又不至于把界面撑爆。 */
    private const val LIMIT = 60

    /** 新→旧。多个线程同时记会丢一条，日志而已，不值得为它加锁。 */
    private var entries: List<Entry> = emptyList()

    /** 记一条。`ip` 为空就忽略（对方没解析出地址，没什么好记的）。 */
    fun record(purpose: String, ip: String?) {
        if (ip.isNullOrBlank()) return
        val entry = Entry(purpose, ip, ImageIps.sourceOf(ip))
        entries = (listOf(entry) + entries).take(LIMIT)
        println("PicaNet: $entry")
    }

    fun snapshot(): List<Entry> = entries

    fun clear() {
        entries = emptyList()
    }
}
