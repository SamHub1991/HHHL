package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

@Immutable
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
    val stats: InstanceStats? = null,
    val onlineUsers: InstanceOnlineUsers? = null,
    val serverInfo: InstanceServerInfo? = null,
)

@Immutable
data class InstanceStats(
    val notesCount: Long = 0,
    val originalNotesCount: Long = 0,
    val usersCount: Long = 0,
    val originalUsersCount: Long = 0,
    val reactionsCount: Long = 0,
    val instances: Long = 0,
    val driveUsageLocal: Long = 0,
    val driveUsageRemote: Long = 0,
)

@Immutable
data class InstanceOnlineUsers(
    val count: Int = 0,
    val countAcrossNetwork: Int = 0,
)

@Immutable
data class InstanceServerInfo(
    val machine: String = "",
    val cpuModel: String = "",
    val cpuCores: Int = 0,
    val memoryTotal: Long = 0,
    val storageTotal: Long = 0,
    val storageUsed: Long = 0,
)

@Immutable
data class InstanceEndpointInfo(
    val params: List<InstanceEndpointParam> = emptyList(),
)

@Immutable
data class InstanceEndpointParam(
    val name: String,
    val type: String,
)

@Immutable
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
