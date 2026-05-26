package cc.hhhl.client.ui.screen

import cc.hhhl.client.fake.FakeData
import kotlin.test.Test
import kotlin.test.assertEquals

class NoteReplyTreePresentationTest {
    @Test
    fun replyDepthFollowsReplyChainFromRoot() {
        val root = FakeData.timeline[0].copy(id = "root")
        val first = FakeData.timeline[1].copy(id = "reply-1", replyId = root.id)
        val second = FakeData.timeline[2].copy(id = "reply-2", replyId = first.id)
        val unrelated = FakeData.timeline[3].copy(id = "reply-3")
        val notesById = listOf(root, first, second, unrelated).associateBy { it.id }

        assertEquals(1, noteReplyDepth(root.id, first, notesById))
        assertEquals(2, noteReplyDepth(root.id, second, notesById))
        assertEquals(1, noteReplyDepth(root.id, unrelated, notesById))
    }

    @Test
    fun replyDepthIsCappedForReadableMobileThreads() {
        val root = FakeData.timeline[0].copy(id = "root")
        val chain = (1..8).runningFold(root) { parent, index ->
            FakeData.timeline[index % FakeData.timeline.size].copy(
                id = "reply-$index",
                replyId = parent.id,
            )
        }
        val notesById = chain.associateBy { it.id }

        assertEquals(4, noteReplyDepth(root.id, chain.last(), notesById))
    }

    @Test
    fun replyTreePresentationKeepsDeepThreadsReadable() {
        val root = FakeData.timeline[0].copy(id = "root")
        val chain = (1..5).runningFold(root) { parent, index ->
            FakeData.timeline[index % FakeData.timeline.size].copy(
                id = "reply-$index",
                replyId = parent.id,
            )
        }
        val notesById = chain.associateBy { it.id }

        val presentation = noteReplyTreePresentation(root.id, chain.last(), notesById)

        assertEquals(4, presentation.depth)
        assertEquals(3, presentation.visualDepth)
        assertEquals(3, presentation.railCount)
        assertEquals(16, presentation.startPaddingDp)
        assertEquals(17, presentation.railGutterWidthDp)
        assertEquals(true, presentation.isDepthCollapsed)
    }

    @Test
    fun replyTreeRailGutterScalesWithVisibleRailsOnly() {
        assertEquals(0, replyTreeRailGutterWidthDp(0))
        assertEquals(2, replyTreeRailGutterWidthDp(1))
        assertEquals(18, replyTreeRailGutterWidthDp(3))
        assertEquals(17, replyTreeRailGutterWidthDp(3, strokeWidthDp = 1, railSpacingDp = 7))
    }

    @Test
    fun visibleReplyThreadOnlyIncludesExpandedChildren() {
        val root = FakeData.timeline[0].copy(id = "root")
        val first = FakeData.timeline[1].copy(id = "reply-1", replyId = root.id)
        val second = FakeData.timeline[2].copy(id = "reply-2", replyId = root.id)
        val child = FakeData.timeline[3].copy(id = "child-1", replyId = first.id)

        assertEquals(
            listOf(first, second),
            visibleReplyThread(
                rootNoteId = root.id,
                replies = listOf(first, second),
                childRepliesByParentId = mapOf(first.id to listOf(child)),
                expandedReplyIds = emptySet(),
            ),
        )
        assertEquals(
            listOf(first, child, second),
            visibleReplyThread(
                rootNoteId = root.id,
                replies = listOf(first, second),
                childRepliesByParentId = mapOf(first.id to listOf(child)),
                expandedReplyIds = setOf(first.id),
            ),
        )
    }

    @Test
    fun timelineThreadItemsNestLoadedRepliesUnderTheirParents() {
        val root = FakeData.timeline[0].copy(id = "root")
        val sibling = FakeData.timeline[1].copy(id = "sibling")
        val child = FakeData.timeline[2].copy(id = "child", replyId = root.id)
        val grandchild = FakeData.timeline[3].copy(id = "grandchild", replyId = child.id)

        val items = timelineThreadItems(listOf(root, sibling, grandchild, child))

        assertEquals(
            listOf(root.id, child.id, grandchild.id, sibling.id),
            items.map { it.note.id },
        )
        assertEquals(
            listOf(1, 2, 3, 1),
            items.map { it.depth },
        )
    }

    @Test
    fun timelineReplyDepthCapsDeepThreads() {
        val root = FakeData.timeline[0].copy(id = "root")
        val chain = (1..8).runningFold(root) { parent, index ->
            FakeData.timeline[index % FakeData.timeline.size].copy(
                id = "reply-$index",
                replyId = parent.id,
            )
        }
        val notesById = chain.associateBy { it.id }

        assertEquals(4, timelineReplyDepth(chain.last(), notesById))
        assertEquals(16, timelineThreadStartPaddingDp(8))
    }

    @Test
    fun timelineThreadPresentationIndentsShallowRepliesAndCapsDeepThreads() {
        val shallow = timelineThreadPresentation(depth = 2)
        val deep = timelineThreadPresentation(depth = 8)

        assertEquals(8, shallow.startPaddingDp)
        assertEquals(1, shallow.railCount)
        assertEquals(false, shallow.isDepthCollapsed)
        assertEquals(16, deep.startPaddingDp)
        assertEquals(2, deep.railCount)
        assertEquals(9, deep.railGutterWidthDp)
        assertEquals(true, deep.isDepthCollapsed)
    }
}
