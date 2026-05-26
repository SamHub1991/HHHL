package cc.hhhl.client.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.TimeZone

class DateLabelsTest {
    @Test
    fun isoInstantIsFormattedInProvidedLocalTimeZone() {
        assertEquals(
            "2026-05-25 09:23",
            "2026-05-25T01:23:45.000Z".toLocalCompactDateLabel(
                TimeZone.of("Asia/Shanghai"),
            ),
        )
    }

    @Test
    fun isoDateWithoutZoneIsTreatedAsUtcFromApi() {
        assertEquals(
            "2026-05-25 09:23",
            "2026-05-25T01:23:45".toLocalCompactDateLabel(
                TimeZone.of("Asia/Shanghai"),
            ),
        )
    }

    @Test
    fun latestUtcTimeWithoutZoneUsesLocalClock() {
        assertEquals(
            "2026-05-26 12:54",
            "2026-05-26T04:54:00.000".toLocalCompactDateLabel(
                TimeZone.of("Asia/Shanghai"),
            ),
        )
    }

    @Test
    fun apiDateSortKeyNormalizesEquivalentInstants() {
        assertEquals(
            "2026-05-25T01:23:45Z",
            apiDateSortKey("2026-05-25T09:23:45+08:00"),
        )
    }
}
