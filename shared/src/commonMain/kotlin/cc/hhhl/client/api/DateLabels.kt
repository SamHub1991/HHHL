package cc.hhhl.client.api

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal fun String.toLocalCompactDateLabel(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val instant = toApiInstantOrNull()
    if (instant != null) return instant.toLocalCompactDateTimeLabel(timeZone)

    if (length >= 16 && getOrNull(10) == 'T') {
        return take(16).replace('T', ' ')
    }
    return this
}

internal fun String.toApiInstantOrNull(): Instant? {
    return runCatching { Instant.parse(this) }.getOrNull()
        ?: parseUtcIsoWithoutZone()
}

internal fun apiDateSortKey(raw: String, fallbackLabel: String = ""): String {
    val instant = raw.toApiInstantOrNull()
    if (instant != null) return instant.toString()
    return raw.ifBlank { fallbackLabel }
}

private fun String.parseUtcIsoWithoutZone(): Instant? {
    if (getOrNull(10) != 'T') return null
    val timePart = drop(10)
    val hasZone = endsWith("Z") ||
        timePart.contains("+") ||
        Regex("-\\d{2}:?\\d{2}$").containsMatchIn(timePart)
    if (hasZone) return null
    return runCatching { Instant.parse("${trimEnd()}Z") }.getOrNull()
}

private fun Instant.toLocalCompactDateTimeLabel(timeZone: TimeZone): String {
    val local = toLocalDateTime(timeZone)
    return "${local.year}-${local.monthNumber.twoDigits()}-${local.dayOfMonth.twoDigits()} " +
        "${local.hour.twoDigits()}:${local.minute.twoDigits()}"
}

private fun Int.twoDigits(): String = if (this < 10) "0$this" else toString()
