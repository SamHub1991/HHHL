package cc.hhhl.client.state

import cc.hhhl.client.model.InstanceMeta
import cc.hhhl.client.repository.InstanceMetaRepository
import cc.hhhl.client.repository.InstanceMetaRepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InstanceMetaUiState(
    val meta: InstanceMeta? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class InstanceMetaStateHolder(
    private val repository: InstanceMetaRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(InstanceMetaUiState())
    val state: StateFlow<InstanceMetaUiState> = mutableState

    fun load() {
        if (state.value.isLoading) return

        mutableState.update {
            it.copy(isLoading = true, errorMessage = null)
        }

        scope.launch {
            when (val result = repository.load()) {
                is InstanceMetaRepositoryResult.Success -> mutableState.update {
                    it.copy(
                        meta = result.meta,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
                is InstanceMetaRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.message,
                    )
                }
            }
        }
    }
}

