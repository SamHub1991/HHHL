package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

@Immutable
data class NotificationItem(
    val id: String,
    val type: NotificationType,
    val actor: User,
    val text: String,
    val createdAtLabel: String,
    val createdAtEpochMillis: Long = 0L,
    val noteId: String? = null,
    val notePreviewText: String? = null,
    val isSpecialCare: Boolean = false,
    val chatRoomId: String? = null,
    val chatUserId: String? = null,
    val chatMessageId: String? = null,
    val isRead: Boolean = false,
)

enum class NotificationType {
    Note,
    Reply,
    Mention,
    Reaction,
    Follow,
    Renote,
    Quote,
    PollEnded,
    FollowRequestReceived,
    FollowRequestAccepted,
    RoleAssigned,
    ChatRoomInvitation,
    AchievementEarned,
    ExportCompleted,
    ImportCompleted,
    Login,
    CreateToken,
    App,
    Edited,
    ScheduledNoteFailed,
    ScheduledNotePosted,
    SharedAccessGranted,
    SharedAccessRevoked,
    SharedAccessLogin,
    ReactionGrouped,
    RenoteGrouped,
    Test,
    Unknown,
}

enum class NotificationFilter(
    val label: String,
    val includedTypes: List<NotificationType> = emptyList(),
) {
    All("全部"),
    Mentions(
        label = "提及",
        includedTypes = listOf(
            NotificationType.Mention,
            NotificationType.Reply,
            NotificationType.Quote,
        ),
    ),
    Reactions(
        label = "互动",
        includedTypes = listOf(
            NotificationType.Reaction,
            NotificationType.ReactionGrouped,
            NotificationType.Renote,
            NotificationType.RenoteGrouped,
        ),
    ),
    SpecialCare(label = "特别关心"),
    Follows(
        label = "关注",
        includedTypes = listOf(
            NotificationType.Follow,
            NotificationType.FollowRequestReceived,
            NotificationType.FollowRequestAccepted,
        ),
    ),
    System(
        label = "系统",
        includedTypes = listOf(
            NotificationType.PollEnded,
            NotificationType.RoleAssigned,
            NotificationType.ChatRoomInvitation,
            NotificationType.AchievementEarned,
            NotificationType.ExportCompleted,
            NotificationType.ImportCompleted,
            NotificationType.Login,
            NotificationType.CreateToken,
            NotificationType.App,
            NotificationType.Edited,
            NotificationType.ScheduledNoteFailed,
            NotificationType.ScheduledNotePosted,
            NotificationType.SharedAccessGranted,
            NotificationType.SharedAccessRevoked,
            NotificationType.SharedAccessLogin,
            NotificationType.Test,
        ),
    ),
}
