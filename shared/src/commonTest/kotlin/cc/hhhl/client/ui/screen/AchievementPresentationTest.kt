package cc.hhhl.client.ui.screen

import cc.hhhl.client.model.Achievement
import kotlin.test.Test
import kotlin.test.assertEquals

class AchievementPresentationTest {
    @Test
    fun lockedAchievementsExposeUnlockDescription() {
        val presentation = achievementRowPresentation(
            Achievement(
                name = "notes10",
                title = "稳定输出",
                description = "发布 10 篇帖子。",
            ),
        )

        assertEquals("稳定输出", presentation.title)
        assertEquals("发布 10 篇帖子。", presentation.description)
        assertEquals("未解锁", presentation.statusLabel)
    }

    @Test
    fun unlockedAchievementsKeepUnlockedDateStatus() {
        val presentation = achievementRowPresentation(
            Achievement(
                name = "notes1",
                title = "第一篇帖子",
                description = "发布第一篇帖子。",
                unlockedAt = "2026-05-29T00:00:00Z",
                unlockedAtLabel = "2026-05-29",
            ),
        )

        assertEquals("第一篇帖子", presentation.title)
        assertEquals("发布第一篇帖子。", presentation.description)
        assertEquals("2026-05-29", presentation.statusLabel)
    }
}
