package cc.hhhl.client.state

import kotlinx.datetime.Instant

enum class ComposePollDeadlinePreset(
    val label: String,
    val durationMillis: Long?,
) {
    Unlimited("不限时", null),
    OneHour("1 小时", 60 * 60 * 1000L),
    OneDay("1 天", 24 * 60 * 60 * 1000L),
    ThreeDays("3 天", 3 * 24 * 60 * 60 * 1000L),
    SevenDays("7 天", 7 * 24 * 60 * 60 * 1000L),
}

fun ComposePollDeadlinePreset.toExpiresAtIso(nowEpochMillis: Long): String? {
    val duration = durationMillis ?: return null
    return Instant.fromEpochMilliseconds(nowEpochMillis + duration).toString()
}
