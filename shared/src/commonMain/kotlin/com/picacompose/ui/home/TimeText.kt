package com.picacompose.ui.home

import com.picacompose.data.pica

/**
 * 时间显示：一天以内说「几秒前 / 几分钟前 / 几小时前」，再往前才是日期。
 *
 * 接口给的 `created_at` 是 UTC 的 ISO 字符串，这里手搓日历换算（不引日期库），
 * 显示日期时按本机时区翻一次，免得晚上发的评论日期差一天。
 */
internal fun relativeTime(iso: String?): String {
    val epoch = parseIsoUtcSeconds(iso) ?: return ""
    val diff = pica.serverNowEpochSeconds() - epoch
    return when {
        diff < 0L -> "刚刚"
        diff < 10L -> "刚刚"
        diff < 60L -> "$diff 秒前"
        diff < 3_600L -> "${diff / 60} 分钟前"
        diff < 86_400L -> "${diff / 3_600} 小时前"
        diff < 172_800L -> "昨天"
        diff < 604_800L -> "${diff / 86_400} 天前"
        else -> dateText(epoch)
    }
}

/** 本机时区下的 `MM/dd HH:mm`。 */
private fun dateText(epochSeconds: Long): String {
    val local = epochSeconds + localUtcOffsetSeconds()
    val days = floorDiv(local, 86_400L)
    val secondsOfDay = local - days * 86_400L
    val (year, month, day) = civilFromDays(days)
    val hour = secondsOfDay / 3_600L
    val minute = (secondsOfDay % 3_600L) / 60L
    return if (year == currentLocalYear()) {
        "${pad2(month)}/${pad2(day)} ${pad2(hour)}:${pad2(minute)}"
    } else {
        "${year}/${pad2(month)}/${pad2(day)}"
    }
}

private fun currentLocalYear(): Int {
    val local = pica.serverNowEpochSeconds() + localUtcOffsetSeconds()
    return civilFromDays(floorDiv(local, 86_400L)).first
}

private fun pad2(value: Long): String = if (value < 10) "0$value" else value.toString()

private fun pad2(value: Int): String = pad2(value.toLong())

private fun floorDiv(a: Long, b: Long): Long {
    val q = a / b
    return if (a % b != 0L && (a xor b) < 0L) q - 1 else q
}

/** `2026-11-08T13:31:27.373Z` / 带 `+08:00` 的都认，只取到秒。 */
private fun parseIsoUtcSeconds(iso: String?): Long? {
    if (iso.isNullOrBlank() || iso.length < 19) return null
    val match = ISO_PATTERN.find(iso) ?: return null
    val year = match.groupValues[1].toIntOrNull() ?: return null
    val month = match.groupValues[2].toIntOrNull() ?: return null
    val day = match.groupValues[3].toIntOrNull() ?: return null
    val hour = match.groupValues[4].toIntOrNull() ?: return null
    val minute = match.groupValues[5].toIntOrNull() ?: return null
    val second = match.groupValues[6].toIntOrNull() ?: return null
    val offsetSeconds = when {
        iso.length > match.range.last + 1 && iso[match.range.last + 1] == 'Z' -> 0L
        else -> {
            val offset = OFFSET_PATTERN.find(iso.substring(match.range.last + 1)) ?: return daysFromCivil(
                year, month, day,
            ) * 86_400L + hour * 3_600L + minute * 60L + second
            val sign = if (offset.groupValues[1] == "-") -1 else 1
            sign * (offset.groupValues[2].toLong() * 3_600L + offset.groupValues[3].toLong() * 60L)
        }
    }
    return daysFromCivil(year, month, day) * 86_400L + hour * 3_600L + minute * 60L + second - offsetSeconds
}

private val ISO_PATTERN = Regex("""(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})""")
private val OFFSET_PATTERN = Regex("""([+-])(\d{2}):?(\d{2})""")

/** Howard Hinnant 的 days_from_civil：公历日期转「1970-01-01 起第几天」。 */
private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    val y = if (month <= 2) year - 1 else year
    val era = floorDiv(y.toLong(), 400L)
    val yoe = y - era * 400
    val mp = (month + 9) % 12
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146_097L + doe - 719_468L
}

/** days_from_civil 的反函数，返回 (年, 月, 日)。 */
private fun civilFromDays(days: Long): Triple<Int, Int, Int> {
    val z = days + 719_468L
    val era = floorDiv(z, 146_097L)
    val doe = z - era * 146_097L
    val yoe = (doe - doe / 1_460L + doe / 36_524L - doe / 146_096L) / 365L
    val y = yoe + era * 400L
    val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
    val mp = (5L * doy + 2L) / 153L
    val d = doy - (153L * mp + 2L) / 5L + 1L
    val m = if (mp < 10L) mp + 3L else mp - 9L
    val year = if (m <= 2L) y + 1L else y
    return Triple(year.toInt(), m.toInt(), d.toInt())
}

/** 本机时区相对 UTC 的偏移（秒）。日期显示要按本机时区走。 */
internal expect fun localUtcOffsetSeconds(): Long
