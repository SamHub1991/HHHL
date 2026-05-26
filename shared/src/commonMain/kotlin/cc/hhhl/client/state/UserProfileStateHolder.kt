package cc.hhhl.client.state

import cc.hhhl.client.model.Note
import cc.hhhl.client.model.User
import cc.hhhl.client.model.UserRelationship
import cc.hhhl.client.repository.UserNotesRepository
import cc.hhhl.client.repository.UserNotesRepositoryResult
import cc.hhhl.client.repository.UserProfileRepository
import cc.hhhl.client.repository.UserProfileRepositoryResult
import cc.hhhl.client.repository.UserRelationshipRepository
import cc.hhhl.client.repository.UserRelationshipRepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserProfileUiState(
    val user: User? = null,
    val relationship: UserRelationship? = null,
    val notes: List<Note> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingNotes: Boolean = false,
    val isLoadingMoreNotes: Boolean = false,
    val isRelationshipLoading: Boolean = false,
    val isRelationshipChanging: Boolean = false,
    val isProfileSaving: Boolean = false,
    val errorMessage: String? = null,
    val profileEditErrorMessage: String? = null,
    val notesErrorMessage: String? = null,
    val message: String? = null,
    val requiresRelogin: Boolean = false,
)

class UserProfileStateHolder(
    private val repository: UserProfileRepository,
    private val notesRepository: UserNotesRepository? = null,
    private val relationshipRepository: UserRelationshipRepository? = null,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(UserProfileUiState())
    val state: StateFlow<UserProfileUiState> = mutableState

    fun load(clearContent: Boolean = false) {
        if (state.value.isLoading) return

        mutableState.update {
            it.copy(
                user = if (clearContent) null else it.user,
                relationship = if (clearContent) null else it.relationship,
                notes = if (clearContent) emptyList() else it.notes,
                isLoading = true,
                isRelationshipLoading = false,
                errorMessage = null,
                profileEditErrorMessage = null,
                notesErrorMessage = null,
                message = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            var shouldRefreshNotes = false
            when (val result = repository.load()) {
                is UserProfileRepositoryResult.Success -> mutableState.update {
                    shouldRefreshNotes = true
                    it.copy(
                        user = result.user,
                        isLoading = false,
                        errorMessage = null,
                        requiresRelogin = false,
                    )
                }
                UserProfileRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is UserProfileRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }

            state.value.user?.let { loadedUser ->
                if (shouldRefreshNotes && relationshipRepository != null) {
                    loadRelationship(loadedUser.id)
                }
            }

            if (shouldRefreshNotes) {
                refreshNotes()
            }
        }
    }

    fun loadMoreNotes() {
        val repository = notesRepository ?: return
        val current = state.value
        if (
            current.isLoading ||
            current.isLoadingNotes ||
            current.isLoadingMoreNotes ||
            current.notes.isEmpty()
        ) {
            return
        }

        mutableState.update {
            it.copy(
                isLoadingMoreNotes = true,
                notesErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyNotesResult(
                result = repository.loadMore(current.notes),
                loadingMore = true,
            )
        }
    }

    fun applyNoteMutation(mutation: NoteLocalMutation) {
        mutableState.update {
            it.copy(
                user = it.user?.copy(
                    pinnedNotes = it.user.pinnedNotes.applyNoteLocalMutation(mutation),
                ),
                notes = it.notes.applyNoteLocalMutation(mutation),
                requiresRelogin = false,
            )
        }
    }

    fun updateProfile(
        name: String,
        description: String,
    ) {
        if (state.value.isProfileSaving) return
        val currentUser = state.value.user
        val cleanName = name.trim()
        val cleanDescription = description.trim()
        if (
            currentUser != null &&
            cleanName == currentUser.displayName.trim() &&
            cleanDescription == currentUser.bio.trim()
        ) {
            mutableState.update {
                it.copy(
                    profileEditErrorMessage = null,
                    message = "资料没有变化",
                    requiresRelogin = false,
                )
            }
            return
        }

        mutableState.update {
            it.copy(
                isProfileSaving = true,
                profileEditErrorMessage = null,
                errorMessage = null,
                message = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyProfileUpdateResult(repository.updateProfile(cleanName, cleanDescription))
        }
    }

    fun clearMessage() {
        mutableState.update {
            it.copy(message = null)
        }
    }

    fun toggleFollow() {
        val relationshipRepository = relationshipRepository ?: return
        val user = state.value.user ?: return
        if (state.value.isRelationshipChanging) return

        mutableState.update {
            it.copy(isRelationshipChanging = true, errorMessage = null, message = null, requiresRelogin = false)
        }

        scope.launch {
            val result = if (user.isFollowing) {
                relationshipRepository.unfollow(user.id)
            } else {
                relationshipRepository.follow(user.id)
            }
            applyRelationshipResult(user, result)
        }
    }

    fun toggleMute() {
        val relationshipRepository = relationshipRepository ?: return
        val user = state.value.user ?: return
        val relationship = state.value.relationship ?: UserRelationship(userId = user.id)
        if (state.value.isRelationshipChanging) return

        mutableState.update {
            it.copy(isRelationshipChanging = true, errorMessage = null, message = null, requiresRelogin = false)
        }

        scope.launch {
            val result = if (relationship.isMuted) {
                relationshipRepository.unmute(user.id)
            } else {
                relationshipRepository.mute(user.id)
            }
            applyMuteResult(user.id, relationship, result)
        }
    }

    fun toggleBlock() {
        val relationshipRepository = relationshipRepository ?: return
        val user = state.value.user ?: return
        val relationship = state.value.relationship ?: UserRelationship(userId = user.id)
        if (state.value.isRelationshipChanging) return

        mutableState.update {
            it.copy(isRelationshipChanging = true, errorMessage = null, message = null, requiresRelogin = false)
        }

        scope.launch {
            val result = if (relationship.isBlocking) {
                relationshipRepository.unblock(user.id)
            } else {
                relationshipRepository.block(user.id)
            }
            applyBlockResult(user, relationship, result)
        }
    }

    fun reportUser() {
        val relationshipRepository = relationshipRepository ?: return
        val user = state.value.user ?: return
        if (state.value.isRelationshipChanging) return

        mutableState.update {
            it.copy(isRelationshipChanging = true, errorMessage = null, message = null, requiresRelogin = false)
        }

        scope.launch {
            applyReportResult(relationshipRepository.reportUser(user.id))
        }
    }

    private suspend fun loadRelationship(userId: String) {
        val repository = relationshipRepository ?: return

        mutableState.update {
            it.copy(isRelationshipLoading = true, requiresRelogin = false)
        }

        applyRelationshipLoadResult(repository.loadRelation(userId))
    }

    private suspend fun refreshNotes() {
        val repository = notesRepository ?: return

        mutableState.update {
            it.copy(isLoadingNotes = true, notesErrorMessage = null, requiresRelogin = false)
        }

        applyNotesResult(
            result = repository.refresh(),
            loadingMore = false,
        )
    }

    private fun applyNotesResult(
        result: UserNotesRepositoryResult,
        loadingMore: Boolean,
    ) {
        when (result) {
            is UserNotesRepositoryResult.Success -> mutableState.update {
                it.copy(
                    notes = result.notes,
                    isLoadingNotes = false,
                    isLoadingMoreNotes = false,
                    notesErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            UserNotesRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isLoadingNotes = false,
                    isLoadingMoreNotes = false,
                    notesErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is UserNotesRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isLoadingNotes = if (loadingMore) it.isLoadingNotes else false,
                    isLoadingMoreNotes = false,
                    notesErrorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applyRelationshipResult(
        originalUser: User,
        result: UserRelationshipRepositoryResult,
    ) {
        when (result) {
            UserRelationshipRepositoryResult.Success -> mutableState.update { current ->
                val currentUser = current.user ?: originalUser
                val nowFollowing = !originalUser.isFollowing
                val followerDelta = if (nowFollowing) 1 else -1
                current.copy(
                    user = currentUser.copy(
                        isFollowing = nowFollowing,
                        followersCount = (currentUser.followersCount + followerDelta).coerceAtLeast(0),
                    ),
                    relationship = (current.relationship ?: UserRelationship(userId = currentUser.id)).copy(
                        isFollowing = nowFollowing,
                    ),
                    isRelationshipChanging = false,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            UserRelationshipRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isRelationshipChanging = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is UserRelationshipRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isRelationshipChanging = false,
                    errorMessage = result.message,
                    requiresRelogin = false,
                )
            }
            is UserRelationshipRepositoryResult.RelationLoaded -> mutableState.update {
                it.copy(
                    relationship = result.relationship,
                    isRelationshipChanging = false,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applyProfileUpdateResult(result: UserProfileRepositoryResult) {
        when (result) {
            is UserProfileRepositoryResult.Success -> mutableState.update { current ->
                current.copy(
                    user = mergeProfileUpdate(current.user, result.user),
                    isProfileSaving = false,
                    profileEditErrorMessage = null,
                    errorMessage = null,
                    message = "资料已保存",
                    requiresRelogin = false,
                )
            }
            UserProfileRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isProfileSaving = false,
                    profileEditErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is UserProfileRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isProfileSaving = false,
                    profileEditErrorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun mergeProfileUpdate(
        current: User?,
        updated: User,
    ): User {
        return current?.copy(
            displayName = updated.displayName,
            username = updated.username,
            avatarInitial = updated.avatarInitial,
            bio = updated.bio,
            avatarUrl = updated.avatarUrl ?: current.avatarUrl,
            bannerUrl = updated.bannerUrl ?: current.bannerUrl,
        ) ?: updated
    }

    private fun applyRelationshipLoadResult(result: UserRelationshipRepositoryResult) {
        when (result) {
            is UserRelationshipRepositoryResult.RelationLoaded -> mutableState.update {
                it.copy(
                    relationship = result.relationship,
                    isRelationshipLoading = false,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            UserRelationshipRepositoryResult.Success -> mutableState.update {
                it.copy(isRelationshipLoading = false, requiresRelogin = false)
            }
            UserRelationshipRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isRelationshipLoading = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is UserRelationshipRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isRelationshipLoading = false,
                    errorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applyMuteResult(
        userId: String,
        originalRelationship: UserRelationship,
        result: UserRelationshipRepositoryResult,
    ) {
        when (result) {
            UserRelationshipRepositoryResult.Success -> mutableState.update { current ->
                val currentRelationship = current.relationship ?: originalRelationship
                current.copy(
                    relationship = currentRelationship.copy(
                        userId = currentRelationship.userId.ifBlank { userId },
                        isMuted = !originalRelationship.isMuted,
                    ),
                    isRelationshipChanging = false,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            UserRelationshipRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isRelationshipChanging = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is UserRelationshipRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isRelationshipChanging = false,
                    errorMessage = result.message,
                    requiresRelogin = false,
                )
            }
            is UserRelationshipRepositoryResult.RelationLoaded -> mutableState.update {
                it.copy(
                    relationship = result.relationship,
                    isRelationshipChanging = false,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applyBlockResult(
        originalUser: User,
        originalRelationship: UserRelationship,
        result: UserRelationshipRepositoryResult,
    ) {
        when (result) {
            UserRelationshipRepositoryResult.Success -> mutableState.update { current ->
                val currentUser = current.user ?: originalUser
                val currentRelationship = current.relationship ?: originalRelationship
                val nowBlocking = !originalRelationship.isBlocking
                val wasFollowing = currentUser.isFollowing
                current.copy(
                    user = currentUser.copy(
                        isFollowing = if (nowBlocking) false else currentUser.isFollowing,
                        followersCount = if (nowBlocking && wasFollowing) {
                            (currentUser.followersCount - 1).coerceAtLeast(0)
                        } else {
                            currentUser.followersCount
                        },
                    ),
                    relationship = currentRelationship.copy(
                        isBlocking = nowBlocking,
                        isFollowing = if (nowBlocking) false else currentRelationship.isFollowing,
                    ),
                    isRelationshipChanging = false,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            UserRelationshipRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isRelationshipChanging = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is UserRelationshipRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isRelationshipChanging = false,
                    errorMessage = result.message,
                    requiresRelogin = false,
                )
            }
            is UserRelationshipRepositoryResult.RelationLoaded -> mutableState.update {
                it.copy(
                    relationship = result.relationship,
                    isRelationshipChanging = false,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applyReportResult(result: UserRelationshipRepositoryResult) {
        when (result) {
            UserRelationshipRepositoryResult.Success -> mutableState.update {
                it.copy(
                    isRelationshipChanging = false,
                    message = "已提交举报",
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            UserRelationshipRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isRelationshipChanging = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is UserRelationshipRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isRelationshipChanging = false,
                    errorMessage = result.message,
                    requiresRelogin = false,
                )
            }
            is UserRelationshipRepositoryResult.RelationLoaded -> mutableState.update {
                it.copy(isRelationshipChanging = false, requiresRelogin = false)
            }
        }
    }
}
