package cc.hhhl.client.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class SpecialCareUiState(
    val userIds: Set<String> = emptySet(),
)

interface SpecialCareStore {
    fun loadSpecialCareUserIds(): Set<String>

    fun saveSpecialCareUserIds(userIds: Set<String>)
}

object NoopSpecialCareStore : SpecialCareStore {
    override fun loadSpecialCareUserIds(): Set<String> = emptySet()

    override fun saveSpecialCareUserIds(userIds: Set<String>) = Unit
}

class SpecialCareStateHolder(
    private val store: SpecialCareStore = NoopSpecialCareStore,
) {
    private val mutableState = MutableStateFlow(SpecialCareUiState())
    val state: StateFlow<SpecialCareUiState> = mutableState

    fun restoreStoredSpecialCare() {
        val restoredUserIds = runCatching { store.loadSpecialCareUserIds().cleanUserIds() }
            .getOrDefault(emptySet())

        mutableState.update { it.copy(userIds = restoredUserIds) }
    }

    fun isSpecialCare(userId: String): Boolean {
        return userId.trim() in state.value.userIds
    }

    fun toggleSpecialCare(userId: String): Boolean {
        val cleanUserId = userId.trim()
        if (cleanUserId.isEmpty()) return false

        val nextUserIds = if (cleanUserId in state.value.userIds) {
            state.value.userIds - cleanUserId
        } else {
            state.value.userIds + cleanUserId
        }
        runCatching { store.saveSpecialCareUserIds(nextUserIds) }
        mutableState.update { it.copy(userIds = nextUserIds) }
        return cleanUserId in nextUserIds
    }

    private fun Set<String>.cleanUserIds(): Set<String> {
        return mapNotNull { it.trim().takeIf(String::isNotEmpty) }.toSet()
    }
}
