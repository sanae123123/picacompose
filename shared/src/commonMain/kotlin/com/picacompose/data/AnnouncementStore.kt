package com.picacompose.data

/**
 * 记住「不再顯示」过的公告 id。
 *
 * 官方 App 也一样，接口没有已读回执，只能本地记。
 */
class AnnouncementStore(private val kv: KeyValueStore = platformKeyValueStore()) {

    fun dismissedIds(): Set<String> = kv.get(KEY_DISMISSED)
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()
        .toSet()

    fun dismiss(id: String) {
        if (id.isEmpty()) return
        kv.put(KEY_DISMISSED, (dismissedIds() + id).joinToString(","))
    }

    private companion object {
        const val KEY_DISMISSED = "notice.dismissed"
    }
}
