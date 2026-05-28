package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

@Immutable
data class FederationInstance(
    val id: String,
    val host: String,
    val name: String?,
    val description: String? = null,
    val softwareName: String?,
    val softwareVersion: String?,
    val usersCount: Int,
    val notesCount: Int,
    val followingCount: Int,
    val followersCount: Int,
    val isNotResponding: Boolean,
    val isSuspended: Boolean,
    val isBlocked: Boolean,
    val isSilenced: Boolean,
    val maintainerName: String? = null,
    val maintainerEmail: String? = null,
    val iconUrl: String? = null,
    val faviconUrl: String? = null,
    val latestRequestReceivedAtLabel: String = "",
    val infoUpdatedAtLabel: String = "",
)

@Immutable
data class FederationFollow(
    val id: String,
    val createdAtLabel: String = "",
    val followeeId: String,
    val followerId: String,
    val followee: User? = null,
    val follower: User? = null,
)

@Immutable
data class FederationStats(
    val topSubInstances: List<FederationInstance>,
    val otherFollowersCount: Int,
    val topPubInstances: List<FederationInstance>,
    val otherFollowingCount: Int,
)
