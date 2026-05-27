package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

@Immutable
data class FavoriteNote(
    val id: String,
    val createdAtLabel: String,
    val note: Note,
)
