package cc.hhhl.client.model

data class FederationInstance(
    val id: String,
    val host: String,
    val name: String?,
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
)
