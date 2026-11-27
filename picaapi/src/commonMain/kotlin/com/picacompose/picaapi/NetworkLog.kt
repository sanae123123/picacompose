package com.picacompose.picaapi

/**
 * 最近几次「连到哪台」和网络事件的记录，设置里的网络链路页照着列。
 *
 * 连接类只在新建连接（`Dns.lookup`）那一刻记一条：连接复用不会再记，所以一条就代表一次
 * 真连出去；事件类记卡顿、降级、换线这些看不出来的关键点。
 * 一行长这样：`[图片] 104.19.53.76 [优选]` / `[线路] 降级 线路 2`。
 */
object NetworkLog {

    /** 一条记录：用途（图片 / 接口 / DoH / 线路）加内容，内容里已经带好来源或描述。 */
    data class Entry(val purpose: String, val text: String) {
        override fun toString(): String = "[$purpose] $text"
    }

    /** 留最近多少条：够看出这几分钟在连哪儿，又不至于把界面撑爆。 */
    private const val LIMIT = 60

    /** 新→旧。多个线程同时记会丢一条，日志而已，不值得为它加锁。 */
    private var entries: List<Entry> = emptyList()

    /** 记一次新连接：地址后面自动带上它从哪一层来。 */
    fun record(purpose: String, ip: String?) {
        if (ip.isNullOrBlank()) return
        add(purpose, "$ip [${ImageIps.sourceOf(ip)}]")
    }

    /** 记一件事：超时、降级、换线这类没有目标地址的关键点。 */
    fun event(purpose: String, text: String) = add(purpose, text)

    private fun add(purpose: String, text: String) {
        val entry = Entry(purpose, text)
        entries = (listOf(entry) + entries).take(LIMIT)
        println("PicaNet: $entry")
    }

    fun snapshot(): List<Entry> = entries

    fun clear() {
        entries = emptyList()
    }
}
