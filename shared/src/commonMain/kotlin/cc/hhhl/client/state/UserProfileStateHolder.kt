package cc.hhhl.client.state

import cc.hhhl.client.api.DriveFileUpload
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.User
import cc.hhhl.client.model.UserRelationship
import cc.hhhl.client.repository.DriveFileRepository
import cc.hhhl.client.repository.DriveFileRepositoryResult
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
    private val driveFileRepository: DriveFileRepository? = null,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(UserProfileUiState())
    val state: StateFlow<UserProfileUiState> = mutableState
    private var profileLoadRequestId = 0
    private var notesRequestId = 0
    private var profileMutationRequestId = 0
    private var relationshipActionRequestId = 0

    fun load(clearContent: Boolean = false) {
        if (!clearContent && state.value.isLoading) return
        val requestId = nextProfileLoadRequestId()
        if (clearContent) {
            nextNotesRequestId()
            profileMutationRequestId += 1
            relationshipActionRequestId += 1
        }

        mutableState.update {
            it.copy(
                user = if (clearContent) null else it.user,
                relationship = if (clearContent) null else it.relationship,
                notes = if (clearContent) emptyList() else it.notes,
                isLoading = true,
                isLoadingNotes = if (clearContent) false else it.isLoadingNotes,
                isLoadingMoreNotes = if (clearContent) false else it.isLoadingMoreNotes,
                isRelationshipLoading = false,
                isRelationshipChanging = if (clearContent) false else it.isRelationshipChanging,
                isProfileSaving = if (clearContent) false else it.isProfileSaving,
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
                    if (requestId != profileLoadRequestId) return@update it
                    shouldRefreshNotes = true
                    it.copy(
                        user = result.user,
                        isLoading = false,
                        errorMessage = null,
                        requiresRelogin = false,
                    )
                }
                UserProfileRepositoryResult.Unauthorized -> mutableState.update {
                    if (requestId != profileLoadRequestId) return@update it
                    it.copy(
                        isLoading = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is UserProfileRepositoryResult.Error -> mutableState.update {
                    if (requestId != profileLoadRequestId) return@update it
                    it.copy(
                        isLoading = false,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }

            if (requestId != profileLoadRequestId) return@launch

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
            val userId = current.user?.id
            val requestId = nextNotesRequestId()
            applyNotesResult(
                result = repository.loadMore(current.notes),
                loadingMore = true,
                userId = userId,
                requestId = requestId,
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
        val mutationRequestId = profileMutationRequestId + 1
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
        profileMutationRequestId = mutationRequestId

        scope.launch {
            applyProfileUpdateResult(
                requestId = mutationRequestId,
                originalUserId = currentUser?.id,
                result = repository.updateProfile(cleanName, cleanDescription),
            )
        }
    }

    fun updateBanner(upload: DriveFileUpload) {
        val driveRepository = driveFileRepository ?: return
        val currentUser = state.value.user ?: return
        if (state.value.isProfileSaving) return
        val mutationRequestId = ++profileMutationRequestId

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
            when (val uploadResult = driveRepository.upload(upload)) {
                is DriveFileRepositoryResult.Success -> {
                    if (!isCurrentProfileMutation(mutationRequestId, currentUser.id)) return@launch
                    applyProfileUpdateResult(
                        requestId = mutationRequestId,
                        originalUserId = currentUser.id,
                        result = repository.updateBanner(
                            name = currentUser.displayName,
                            description = currentUser.bio,
                            bannerId = uploadResult.file.id,
                        ),
                    )
                }
                DriveFileRepositoryResult.Unauthorized -> mutableState.update {
                    if (!isCurrentProfileMutation(mutationRequestId, currentUser.id)) return@update it
                    it.copy(
                        isProfileSaving = false,
                        profileEditErrorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is DriveFileRepositoryResult.ValidationError -> mutableState.update {
                    if (!isCurrentProfileMutation(mutationRequestId, currentUser.id)) return@update it
                    it.copy(
                        isProfileSaving = false,
                        profileEditErrorMessage = uploadResult.message,
                        requiresRelogin = false,
                    )
                }
                is DriveFileRepositoryResult.Error -> mutableState.update {
                    if (!isCurrentProfileMutation(mutationRequestId, currentUser.id)) return@update it
                    it.copy(
                        isProfileSaving = false,
                        profileEditErrorMessage = uploadResult.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun showProfileEditError(message: String) {
        mutableState.update {
            it.copy(
                profileEditErrorMessage = message,
                message = null,
                requiresRelogin = false,
            )
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
        val requestId = ++relationshipActionRequestId

        mutableState.update {
            it.copy(isRelationshipChanging = true, errorMessage = null, message = null, requiresRelogin = false)
        }

        scope.launch {
            val result = if (user.isFollowing) {
                relationshipRepository.unfollow(user.id)
            } else {
                relationshipRepository.follow(user.id)
            }
            applyRelationshipResult(requestId, user, result)
        }
    }

    fun toggleMute() {
        val relationshipRepository = relationshipRepository ?: return
        val user = state.value.user ?: return
        val relationship = state.value.relationship ?: UserRelationship(userId = user.id)
        if (state.value.isRelationshipChanging) return
        val requestId = ++relationshipActionRequestId

        mutableState.update {
            it.copy(isRelationshipChanging = true, errorMessage = null, message = null, requiresRelogin = false)
        }

        scope.launch {
            val result = if (relationship.isMuted) {
                relationshipRepository.unmute(user.id)
            } else {
                relationshipRepository.mute(user.id)
            }
            applyMuteResult(requestId, user.id, relationship, result)
        }
    }

    fun toggleBlock() {
        val relationshipRepository = relationshipRepository ?: return
        val user = state.value.user ?: return
        val relationship = state.value.relationship ?: UserRelationship(userId = user.id)
        if (state.value.isRelationshipChanging) return
        val requestId = ++relationshipActionRequestId

        mutableState.update {
            it.copy(isRelationshipChanging = true, errorMessage = null, message = null, requiresRelogin = false)
        }

        scope.launch {
            val result = if (relationship.isBlocking) {
                relationshipRepository.unblock(user.id)
            } else {
                relationshipRepository.block(user.id)
            }
            applyBlockResult(requestId, user, relationship, result)
        }
    }

    fun reportUser() {
        val relationshipRepository = relationshipRepository ?: return
        val user = state.value.user ?: return
        if (state.value.isRelationshipChanging) return
        val requestId = ++relationshipActionRequestId

        mutableState.update {
            it.copy(isRelationshipChanging = true, errorMessage = null, message = null, requiresRelogin = false)
        }

        scope.launch {
            applyReportResult(requestId, user.id, relationshipRepository.reportUser(user.id))
        }
    }

    private suspend fun loadRelationship(userId: String) {
        val repository = relationshipRepository ?: return

        mutableState.update {
            it.copy(isRelationshipLoading = true, requiresRelogin = false)
        }

        applyRelationshipLoadResult(userId, repository.loadRelation(userId))
    }

    private suspend fun refreshNotes() {
        val repository = notesRepository ?: return
        val userId = state.value.user?.id
        val requestId = nextNotesRequestId()

        mutableState.update {
            it.copy(isLoadingNotes = true, notesErrorMessage = null, requiresRelogin = false)
        }

        applyNotesResult(
            result = repository.refresh(),
            loadingMore = false,
            userId = userId,
            requestId = requestId,
        )
    }

    private fun applyNotesResult(
        result: UserNotesRepositoryResult,
        loadingMore: Boolean,
        userId: String?,
        requestId: Int,
    ) {
        if (requestId != notesRequestId || state.value.user?.id != userId) return
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
        requestId: Int,
        originalUser: User,
        result: UserRelationshipRepositoryResult,
    ) {
        if (!isCurrentRelationshipAction(requestId, originalUser.id)) return
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

    private fun applyProfileUpdateResult(
        requestId: Int,
        originalUserId: String?,
        result: UserProfileRepositoryResult,
    ) {
        when (result) {
            is UserProfileRepositoryResult.Success -> mutableState.update { current ->
                if (!isCurrentProfileMutation(requestId, originalUserId)) return@update current
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
                if (!isCurrentProfileMutation(requestId, originalUserId)) return@update it
                it.copy(
                    isProfileSaving = false,
                    profileEditErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is UserProfileRepositoryResult.Error -> mutableState.update {
                if (!isCurrentProfileMutation(requestId, originalUserId)) return@update it
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

    private fun applyRelationshipLoadResult(
        userId: String,
        result: UserRelationshipRepositoryResult,
    ) {
        if (state.value.user?.id != userId) return
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
        requestId: Int,
        userId: String,
        originalRelationship: UserRelationship,
        result: UserRelationshipRepositoryResult,
    ) {
        if (!isCurrentRelationshipAction(requestId, userId)) return
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
        requestId: Int,
        originalUser: User,
        originalRelationship: UserRelationship,
        result: UserRelationshipRepositoryResult,
    ) {
        if (!isCurrentRelationshipAction(requestId, originalUser.id)) return
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

    private fun applyReportResult(
        requestId: Int,
        userId: String,
        result: UserRelationshipRepositoryResult,
    ) {
        if (!isCurrentRelationshipAction(requestId, userId)) return
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

    private fun isCurrentProfileMutation(
        requestId: Int,
        originalUserId: String?,
    ): Boolean {
        if (requestId != profileMutationRequestId) return false
        return originalUserId == null || state.value.user?.id == originalUserId
    }

    private fun isCurrentRelationshipAction(
        requestId: Int,
        userId: String,
    ): Boolean {
        return requestId == relationshipActionRequestId && state.value.user?.id == userId
    }

    private fun nextProfileLoadRequestId(): Int {
        profileLoadRequestId += 1
        return profileLoadRequestId
    }

    private fun nextNotesRequestId(): Int {
        notesRequestId += 1
        return notesRequestId
    }
}
