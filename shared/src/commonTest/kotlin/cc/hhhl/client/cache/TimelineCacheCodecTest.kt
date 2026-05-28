package cc.hhhl.client.cache

import cc.hhhl.client.api.TimelineKind
import cc.hhhl.client.api.toLocalCompactDateLabel
import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.NoteMedia
import cc.hhhl.client.model.NotePoll
import cc.hhhl.client.model.NotePollChoice
import cc.hhhl.client.model.NoteReaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimelineCacheCodecTest {
    @Test
    fun encodesAndDecodesTimelineSnapshotsByKind() {
        val snapshots = mapOf(
            TimelineKind.Home to listOf(FakeData.timeline[0]),
            TimelineKind.Local to listOf(FakeData.timeline[1]),
        )

        val payload = TimelineCacheCodec.encode(snapshots)
        val decoded = TimelineCacheCodec.decode(payload)

        assertEquals(snapshots, decoded)
    }

    @Test
    fun invalidPayloadDecodesToEmptySnapshots() {
        val decoded = TimelineCacheCodec.decode("not-json")

        assertTrue(decoded.isEmpty())
    }

    @Test
    fun codecTrimsTimelineSnapshotsPerKind() {
        val notes = (0 until 130).map { index -> FakeData.timeline[0].copy(id = "note-$index") }

        val decoded = TimelineCacheCodec.decode(
            TimelineCacheCodec.encode(mapOf(TimelineKind.Home to notes)),
        )

        val restored = decoded.getValue(TimelineKind.Home)
        assertEquals(120, restored.size)
        assertEquals("note-0", restored.first().id)
        assertEquals("note-119", restored.last().id)
    }

    @Test
    fun preservesTimelinePresentationFieldsAcrossColdRestore() {
        val quoted = FakeData.timeline[1].copy(
            media = listOf(
                NoteMedia(
                    id = "quoted-media",
                    description = "quoted alt",
                    type = "image/webp",
                    url = "https://dc.hhhl.cc/files/quoted.webp",
                    thumbnailUrl = "https://dc.hhhl.cc/thumb/quoted.webp",
                    isSensitive = true,
                ),
            ),
        )
        val note = FakeData.timeline[0].copy(
            author = FakeData.timeline[0].author.copy(
                bannerUrl = "https://dc.hhhl.cc/banner.webp",
            ),
            media = listOf(
                NoteMedia(
                    id = "media-1",
                    description = "alt text",
                    type = "image/png",
                    url = "https://dc.hhhl.cc/files/image.png",
                    thumbnailUrl = "https://dc.hhhl.cc/thumb.webp",
                    isSensitive = true,
                ),
            ),
            reactions = listOf(NoteReaction("👍", 4)),
            myReaction = "👍",
            isFavorited = true,
            poll = NotePoll(
                multiple = true,
                expiresAtLabel = "2026-05-26 00:00",
                choices = listOf(
                    NotePollChoice("A", votes = 4, isVoted = true),
                    NotePollChoice("B", votes = 2, isVoted = false),
                ),
            ),
            quotedNote = quoted,
        )

        val decoded = TimelineCacheCodec.decode(
            TimelineCacheCodec.encode(mapOf(TimelineKind.Home to listOf(note))),
        )

        assertEquals(note, decoded[TimelineKind.Home]?.single())
    }

    @Test
    fun decodesRawIsoTimesThroughLocalFormatterWhenAvailable() {
        val createdAt = "2026-05-25T01:23:45.000Z"
        val expiresAt = "2026-05-26T04:54:00.000Z"
        val note = FakeData.timeline[0].copy(
            createdAt = createdAt,
            createdAtLabel = "stale cached label",
            poll = NotePoll(
                multiple = false,
                expiresAt = expiresAt,
                expiresAtLabel = "stale poll label",
                choices = listOf(NotePollChoice("A", votes = 1, isVoted = false)),
            ),
        )

        val decoded = TimelineCacheCodec.decode(
            TimelineCacheCodec.encode(mapOf(TimelineKind.Home to listOf(note))),
        )[TimelineKind.Home]?.single()

        assertEquals(createdAt, decoded?.createdAt)
        assertEquals(createdAt.toLocalCompactDateLabel(), decoded?.createdAtLabel)
        assertEquals(expiresAt, decoded?.poll?.expiresAt)
        assertEquals(expiresAt.toLocalCompactDateLabel(), decoded?.poll?.expiresAtLabel)
    }
}
