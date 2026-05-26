package cc.hhhl.client.model

data class InstanceMeta(
    val name: String,
    val description: String,
    val version: String,
    val iconUrl: String? = null,
    val themeColor: String? = null,
    val maxNoteTextLength: Int = 3000,
    val maxCwLength: Int = 500,
    val defaultLike: String = "❤️",
    val noteSearchableScope: String? = null,
    val capabilities: InstanceCapabilities = InstanceCapabilities(),
)

data class InstanceCapabilities(
    val miauthEnabled: Boolean = true,
    val localTimelineAvailable: Boolean = true,
    val globalTimelineAvailable: Boolean = true,
    val bubbleTimelineAvailable: Boolean = false,
    val canPublicNote: Boolean = true,
    val canSearchNotes: Boolean = true,
    val chatAvailable: Boolean = false,
    val canTrend: Boolean = false,
    val canViewFederation: Boolean = false,
    val clipLimit: Int = 0,
    val antennaLimit: Int = 0,
    val userListLimit: Int = 0,
    val userEachUserListsLimit: Int = 0,
    val scheduleNoteMax: Int = 0,
    val driveCapacityMb: Int = 0,
    val maxFileSizeMb: Int = 0,
) {
    val canUseClips: Boolean
        get() = clipLimit > 0

    val canUseAntennas: Boolean
        get() = antennaLimit > 0

    val canUseUserLists: Boolean
        get() = userListLimit > 0

    val canScheduleNotes: Boolean
        get() = scheduleNoteMax > 0
}
