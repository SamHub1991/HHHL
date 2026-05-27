package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

@Immutable
data class UserRelationshipListEntry(
    val id: String,
    val user: User,
    val createdAtLabel: String = "",
)
