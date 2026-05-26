package cc.hhhl.client.model

data class UserRelationshipListEntry(
    val id: String,
    val user: User,
    val createdAtLabel: String = "",
)
