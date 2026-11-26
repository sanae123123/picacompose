package com.picacompose.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.picacompose.picaapi.nowEpochSeconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * 后台图片 IP 优选的运行状态，设置页的小卡片就显示这一份。
 *
 * 只用来展示：真正的地址选择在 [com.picacompose.picaapi.ImageIps] 里，这里写错也不影响加载。
 */
internal object ImageIpsStatus {

    enum class Phase {
        /** 还没开跑（刚进主界面、扫描延的那 3 秒里）。 */
        Idle,

        /** 正在测。 */
        Scanning,

        /** 测完了，[pool] 里是这一轮选出来的地址。 */
        Done,

        /** 这一轮一个能用的都没测出来，池子保持上一轮（或官方源）。 */
        Failed,

        /** 设置里把开关关了。 */
        Off,
    }

    var phase: Phase by mutableStateOf(Phase.Idle)
        private set

    /** 这一轮已经测了几台。 */
    var tested: Int by mutableStateOf(0)
        private set

    /** 这一轮一共几台候选。 */
    var total: Int by mutableStateOf(0)
        private set

    /** 这一轮选出来的地址。 */
    var pool: List<String> by mutableStateOf(emptyList())
        private set

    /** 这一轮测到的最快握手耗时。 */
    var fastestMs: Long? by mutableStateOf(null)
        private set

    /** 这一轮是什么时候测完的，用来显示「几分钟前」。 */
    var finishedAt: TimeMark? by mutableStateOf(null)
        private set

    /** 上次测完的绝对时间（epoch 秒）。进程重启后 [finishedAt] 没了，靠它接着算「几分钟前」。 */
    var lastFinishedAtEpochSeconds: Long? by mutableStateOf(null)
        private set

    /**
     * 冷启动时把上一轮的结果先摆出来：设置页的「地址池」和「上次完成」
     * 不用等这一轮测完才有内容。
     */
    fun restore(pool: List<String>, finishedAtEpochSeconds: Long?) {
        if (pool.isNotEmpty()) this.pool = pool
        this.lastFinishedAtEpochSeconds = finishedAtEpochSeconds
    }

    fun start(total: Int) {
        phase = Phase.Scanning
        this.total = total
        tested = 0
        fastestMs = null
    }

    /**
     * 扫描进度。[live] 是已经测出来、正在被用的那几台 —— 边测边用，不用等全扫完。
     */
    fun progress(tested: Int, fastestMs: Long?, live: List<String>? = null) {
        this.tested = tested
        if (fastestMs != null) this.fastestMs = fastestMs
        if (live != null && live.isNotEmpty()) this.pool = live
    }

    fun finish(pool: List<String>) {
        this.pool = pool
        phase = if (pool.isEmpty()) Phase.Failed else Phase.Done
        finishedAt = TimeSource.Monotonic.markNow()
        lastFinishedAtEpochSeconds = nowEpochSeconds()
    }

    /** 开关关掉时调一次：卡片显示成「已关闭」，并清掉上一轮的结果。 */
    fun off() {
        phase = Phase.Off
        pool = emptyList()
        tested = 0
        total = 0
        fastestMs = null
        finishedAt = null
    }
}
