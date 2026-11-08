package com.picacompose.data

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 消息红点：拿通知列表最新一条的 id 和「上次看过的那条」比，对不上就亮。
 *
 * 接口里没有已读/未读字段，只能这么判断。看不看过的记录落盘，重启 App 也还认。
 */
@Stable
class NoticeUnread {
    var hasUnread by mutableStateOf(false)
        private set

    var newestId by mutableStateOf<String?>(null)
        private set

    /** 拉一下最新的通知，更新红点状态。 */
    suspend fun refresh() {
        runCatching { pica.users.notifications(page = 1) }
            .onSuccess { paged -> update(paged.docs.firstOrNull()?.id) }
    }

    /** 消息页拉到第一页后把最新那条记成已看，红点灭掉。 */
    fun markSeen(id: String) {
        if (id.isBlank()) return
        newestId = id
        platformKeyValueStore().put(SEEN_KEY, id)
        hasUnread = false
    }

    private fun update(newest: String?) {
        newestId = newest
        hasUnread = newest != null && newest != platformKeyValueStore().get(SEEN_KEY)
    }

    private companion object {
        const val SEEN_KEY = "notice.seenNotificationId"
    }
}
