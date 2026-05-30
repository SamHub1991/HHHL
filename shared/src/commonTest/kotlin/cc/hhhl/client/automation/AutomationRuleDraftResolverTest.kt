package cc.hhhl.client.automation

import cc.hhhl.client.model.Channel
import cc.hhhl.client.model.ChatRoom
import cc.hhhl.client.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class AutomationRuleDraftResolverTest {
    @Test
    fun resolvesRoomUserAndChannelNamesToIds() = runTest {
        val room = sampleRoom(id = "room-1", name = "总部")
        val user = sampleUser(id = "user-1", displayName = "小张", username = "zhang")
        val channel = sampleChannel(id = "channel-1", name = "研发频道")

        val result = resolveAutomationRuleDraft(
            rule = AutomationRule(
                id = "rule-draft",
                name = "总部消息",
                trigger = AutomationTrigger.ChatMessage,
                conditions = listOf(
                    AutomationCondition("condition-room", AutomationConditionType.RoomNameContains, "总部"),
                    AutomationCondition("condition-user", AutomationConditionType.SenderNameContains, "zhang"),
                    AutomationCondition("condition-channel", AutomationConditionType.ChannelNameContains, "研发频道"),
                ),
                actions = listOf(
                    AutomationAction(
                        id = "action-channel",
                        type = AutomationActionType.PostToChannel,
                        targetId = "channel:研发频道",
                    ),
                ),
            ),
            input = AutomationRuleDraftResolveInput(
                rooms = listOf(room),
                users = listOf(user),
                channels = listOf(channel),
            ),
        )

        assertEquals(AutomationConditionType.RoomId, result.rule.conditions[0].type)
        assertEquals("room-1", result.rule.conditions[0].value)
        assertEquals(AutomationConditionType.SenderUserId, result.rule.conditions[1].type)
        assertEquals("user-1", result.rule.conditions[1].value)
        assertEquals(AutomationConditionType.ChannelId, result.rule.conditions[2].type)
        assertEquals("channel-1", result.rule.conditions[2].value)
        assertEquals("channel-1", result.rule.actions.single().targetId)
    }

    @Test
    fun allUsersDisablesSenderNameCondition() = runTest {
        val result = resolveAutomationRuleDraft(
            rule = AutomationRule(
                id = "rule-draft",
                name = "任意用户",
                trigger = AutomationTrigger.ChatMessage,
                conditions = listOf(
                    AutomationCondition("condition-user", AutomationConditionType.SenderNameContains, "全部用户"),
                ),
            ),
            input = AutomationRuleDraftResolveInput(),
        )

        assertEquals(false, result.rule.conditions.single().enabled)
        assertEquals("", result.rule.conditions.single().value)
    }
}

private fun sampleUser(
    id: String,
    displayName: String,
    username: String,
): User {
    return User(
        id = id,
        displayName = displayName,
        username = username,
        avatarInitial = displayName.firstOrNull()?.toString().orEmpty(),
    )
}

private fun sampleRoom(
    id: String,
    name: String,
): ChatRoom {
    return ChatRoom(
        id = id,
        membershipId = "membership-$id",
        name = name,
        description = "",
        joinMode = "public",
        memberCount = 1,
        isMuted = false,
        owner = sampleUser("owner-$id", "Owner", "owner"),
    )
}

private fun sampleChannel(
    id: String,
    name: String,
): Channel {
    return Channel(
        id = id,
        name = name,
        description = "",
        color = "#40c057",
        userId = null,
        bannerUrl = null,
        pinnedNoteIds = emptyList(),
        pinnedNotes = emptyList(),
        isArchived = false,
        isSensitive = false,
        allowRenoteToExternal = true,
        isFollowing = false,
        isFavorited = false,
        hasUnreadNote = false,
        usersCount = 0,
        notesCount = 0,
    )
}
