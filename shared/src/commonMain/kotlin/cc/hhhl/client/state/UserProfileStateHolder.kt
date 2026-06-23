package cc.hhhl.client.state

import cc.hhhl.client.api.DriveFileUpload
import cc.hhhl.client.media.ImageProcessor
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.User
import cc.hhhl.client.model.UserRelationship
import cc.hhhl.client.repository.DriveFileRepository
import cc.hhhl.client.repository.DriveFileRepositoryResult
import cc.hhhl.client.repository.DriveManagementRepositoryResult
import cc.hhhl.client.repository.UserNotesRepository
import cc.hhhl.client.repository.UserNotesRepositoryResult
import cc.hhhl.client.repository.UserProfileRepository
import cc.hhhl.client.repository.UserProfileRepositoryResult
import cc.hhhl.client.repository.UserRelationshipRepository
import cc.hhhl.client.repository.UserRelationshipRepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/** 头像上传冷却时间（秒） */
const val AVATAR_UPLOAD_COOLDOWN_SECONDS = 5

/** 每日头像上传次数限制 */
const val AVATAR_DAILY_UPLOAD_LIMIT = 5

/** 头像上传允许的文件格式 */
val AVATAR_ALLOWED_CONTENT_TYPES = listOf("image/jpeg", "image/png", "image/gif", "image/webp")

/** 头像文件最大大小（5MB） */
const val AVATAR_MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024L

/** 头像文件夹名称 */
const val AVATAR_FOLDER_NAME = "avatars"

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
    /** 头像上传冷却剩余秒数，0 表示可以上传 */
    val avatarUploadCooldownSeconds: Int = 0,
    /** 今日头像上传剩余次数 */
    val avatarDailyUploadRemaining: Int = AVATAR_DAILY_UPLOAD_LIMIT,
    /** 待确认上传的头像文件，非 null 时 UI 应显示预览确认对话框 */
    val pendingAvatarUpload: DriveFileUpload? = null,
)

class UserProfileStateHolder(
    private val repository: UserProfileRepository,
    private val notesRepository: UserNotesRepository? = null,
    private val relationshipRepository: UserRelationshipRepository? = null,
    private val driveFileRepository: DriveFileRepository? = null,
    private val imageProcessor: ImageProcessor? = null,
    private val scope: CoroutineScope,
    private val timeProvider: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val mutableState = MutableStateFlow(UserProfileUiState())
    val state: StateFlow<UserProfileUiState> = mutableState
    private var profileLoadRequestId = 0
    private var notesRequestId = 0
    private var profileMutationRequestId = 0
    private var relationshipActionRequestId = 0
    
    // 头像上传限制追踪
    private var lastAvatarUploadTimeMillis: Long = 0L
    private var dailyAvatarUploadCount: Int = 0
    private var dailyUploadResetDateMillis: Long = 0L
    private var lastUploadedAvatarId: String? = null
    /** 头像文件夹 ID，缓存以避免重复创建 */
    private var avatarFolderId: String? = null

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

    /**
     * 设置待确认上传的头像文件
     * 选图后调用此方法，UI 会显示预览确认对话框
     * @param upload 头像文件上传数据
     */
    fun setPendingAvatar(upload: DriveFileUpload) {
        // 先校验文件，校验失败直接提示错误
        val validationError = validateAvatarFile(upload)
        if (validationError != null) {
            mutableState.update {
                it.copy(
                    profileEditErrorMessage = validationError,
                    message = null,
                    requiresRelogin = false,
                )
            }
            return
        }
        // 校验通过，设置待确认状态
        mutableState.update {
            it.copy(pendingAvatarUpload = upload)
        }
    }

    /**
     * 确认上传待确认的头像
     * 清除待确认状态后直接调用 updateAvatar，图片裁剪和压缩统一由 processAvatarImage 处理
     */
    fun confirmPendingAvatar() {
        val pending = state.value.pendingAvatarUpload ?: return
        mutableState.update { it.copy(pendingAvatarUpload = null) }
        updateAvatar(pending)
    }

    /**
     * 取消待确认的头像上传
     */
    fun cancelPendingAvatar() {
        mutableState.update { it.copy(pendingAvatarUpload = null) }
    }

    /**
     * 更新用户头像
     * 先校验文件格式和大小，再检查冷却时间和每日次数限制，
     * 然后进行图片压缩（如果可用），获取或创建头像文件夹，
     * 上传文件到 Drive，更新用户资料的头像字段，
     * 最后删除旧头像文件释放存储空间
     * @param upload 头像文件上传数据
     */
    fun updateAvatar(upload: DriveFileUpload) {
        val driveRepository = driveFileRepository ?: return
        val currentUser = state.value.user ?: return
        if (state.value.isProfileSaving) return
        
        // 文件校验
        val validationError = validateAvatarFile(upload)
        if (validationError != null) {
            mutableState.update {
                it.copy(
                    profileEditErrorMessage = validationError,
                    message = null,
                    requiresRelogin = false,
                )
            }
            return
        }
        
        // 重置每日计数器（如果跨天）
        resetDailyUploadCountIfNeeded()
        
        // 检查冷却时间
        val now = currentTimeMillis()
        val cooldownRemaining = calculateCooldownRemaining(now)
        if (cooldownRemaining > 0) {
            mutableState.update {
                it.copy(
                    profileEditErrorMessage = "请等待 ${cooldownRemaining} 秒后再上传头像",
                    message = null,
                    requiresRelogin = false,
                )
            }
            return
        }
        
        // 检查每日次数限制
        if (dailyAvatarUploadCount >= AVATAR_DAILY_UPLOAD_LIMIT) {
            mutableState.update {
                it.copy(
                    profileEditErrorMessage = "今日头像上传次数已达上限（${AVATAR_DAILY_UPLOAD_LIMIT}次），请明天再试",
                    message = null,
                    requiresRelogin = false,
                )
            }
            return
        }
        
        val mutationRequestId = ++profileMutationRequestId
        val oldAvatarId = lastUploadedAvatarId

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
            // 1. 图片压缩（如果可用）
            val processedUpload = processAvatarImage(upload)
            
            // 2. 获取或创建头像文件夹
            val folderId = getOrCreateAvatarFolder(driveRepository)
            
            // 3. 创建带文件夹 ID 的上传对象
            val finalUpload = if (folderId != null) {
                processedUpload.copy(folderId = folderId)
            } else {
                processedUpload
            }
            
            // 4. 上传文件
            when (val uploadResult = driveRepository.upload(finalUpload)) {
                is DriveFileRepositoryResult.Success -> {
                    if (!isCurrentProfileMutation(mutationRequestId, currentUser.id)) return@launch
                    val updateResult = repository.updateAvatar(
                        name = currentUser.displayName,
                        description = currentUser.bio,
                        avatarId = uploadResult.file.id,
                    )
                    // 如果更新成功但返回的 avatarUrl 为空，使用上传文件的 URL
                    val finalResult = if (updateResult is UserProfileRepositoryResult.Success &&
                        updateResult.user.avatarUrl == null &&
                        uploadResult.file.url != null
                    ) {
                        UserProfileRepositoryResult.Success(
                            updateResult.user.copy(avatarUrl = uploadResult.file.url),
                        )
                    } else {
                        updateResult
                    }
                    applyProfileUpdateResult(
                        requestId = mutationRequestId,
                        originalUserId = currentUser.id,
                        result = finalResult,
                    )
                    // 更新成功后，删除旧头像文件
                    if (oldAvatarId != null && oldAvatarId != uploadResult.file.id) {
                        driveRepository.deleteFile(oldAvatarId)
                    }
                    // 记录新头像 ID，以便下次上传时删除
                    lastUploadedAvatarId = uploadResult.file.id
                    // 更新上传计数和冷却时间
                    onAvatarUploadSuccess()
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
    
    /**
     * 处理头像图片（压缩和裁剪）
     * @param upload 原始上传数据
     * @return 处理后的上传数据
     */
    private suspend fun processAvatarImage(upload: DriveFileUpload): DriveFileUpload {
        val processor = imageProcessor ?: return upload
        
        return try {
            // 先裁剪为正方形
            val croppedResult = processor.cropToSquare(
                imageData = upload.bytes,
                contentType = upload.contentType,
            )
            
            // 再压缩
            val compressedResult = processor.compressImage(
                imageData = croppedResult.bytes,
                contentType = croppedResult.contentType,
                maxWidth = ImageProcessor.DEFAULT_MAX_WIDTH,
                maxHeight = ImageProcessor.DEFAULT_MAX_HEIGHT,
                quality = ImageProcessor.DEFAULT_QUALITY,
            )
            
            upload.copy(
                bytes = compressedResult.bytes,
                contentType = compressedResult.contentType,
            )
        } catch (e: Exception) {
            // 处理失败时返回原始上传数据
            upload
        }
    }
    
    /**
     * 获取或创建头像文件夹
     * @param driveRepository Drive 文件仓库
     * @return 文件夹 ID，如果创建失败返回 null
     */
    private suspend fun getOrCreateAvatarFolder(driveRepository: DriveFileRepository): String? {
        // 如果已有缓存的文件夹 ID，直接返回
        avatarFolderId?.let { return it }
        
        return try {
            // 尝试创建文件夹
            val result = driveRepository.createFolder(
                name = AVATAR_FOLDER_NAME,
                parentId = null,
            )
            
            when (result) {
                is DriveManagementRepositoryResult.FolderCreated -> {
                    avatarFolderId = result.folder.id
                    result.folder.id
                }
                else -> null
            }
        } catch (e: Exception) {
            // 创建失败时返回 null，头像将上传到根目录
            null
        }
    }
    
    /**
     * 校验头像文件
     * @return 错误信息，如果校验通过返回 null
     */
    private fun validateAvatarFile(upload: DriveFileUpload): String? {
        if (upload.bytes.isEmpty()) {
            return "文件内容为空"
        }
        if (upload.bytes.size > AVATAR_MAX_FILE_SIZE_BYTES) {
            val maxSizeMB = AVATAR_MAX_FILE_SIZE_BYTES / (1024 * 1024)
            return "文件大小超过限制（最大 ${maxSizeMB}MB）"
        }
        val contentType = upload.contentType.lowercase()
        if (contentType !in AVATAR_ALLOWED_CONTENT_TYPES) {
            return "不支持的文件格式，仅允许 JPG、PNG、GIF、WebP"
        }
        return null
    }
    
    /**
     * 重置每日上传计数器（如果跨天）
     */
    private fun resetDailyUploadCountIfNeeded() {
        val today = getStartOfTodayMillis()
        if (dailyUploadResetDateMillis < today) {
            dailyAvatarUploadCount = 0
            dailyUploadResetDateMillis = today
            mutableState.update {
                it.copy(avatarDailyUploadRemaining = AVATAR_DAILY_UPLOAD_LIMIT)
            }
        }
    }
    
    /**
     * 计算冷却剩余秒数
     */
    private fun calculateCooldownRemaining(now: Long): Int {
        if (lastAvatarUploadTimeMillis == 0L) return 0
        val elapsedSeconds = (now - lastAvatarUploadTimeMillis) / 1000
        val remaining = AVATAR_UPLOAD_COOLDOWN_SECONDS - elapsedSeconds
        return remaining.coerceAtLeast(0).toInt()
    }
    
    /**
     * 头像上传成功后更新计数和冷却时间
     */
    private fun onAvatarUploadSuccess() {
        lastAvatarUploadTimeMillis = currentTimeMillis()
        dailyAvatarUploadCount++
        val remaining = (AVATAR_DAILY_UPLOAD_LIMIT - dailyAvatarUploadCount).coerceAtLeast(0)
        mutableState.update {
            it.copy(
                avatarUploadCooldownSeconds = AVATAR_UPLOAD_COOLDOWN_SECONDS,
                avatarDailyUploadRemaining = remaining,
            )
        }
        // 启动冷却倒计时
        startCooldownTimer()
    }
    
    /**
     * 启动冷却倒计时
     */
    private fun startCooldownTimer() {
        scope.launch {
            for (i in AVATAR_UPLOAD_COOLDOWN_SECONDS downTo 1) {
                delay(1000L)
                mutableState.update {
                    it.copy(avatarUploadCooldownSeconds = i - 1)
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
    
    /**
     * 获取当前时间戳（毫秒），通过注入的 timeProvider 获取，便于测试
     */
    private fun currentTimeMillis(): Long = timeProvider()

    /**
     * 获取今日起始时间戳（毫秒），基于 timeProvider 提供的时间计算
     */
    private fun getStartOfTodayMillis(): Long {
        val nowMillis = timeProvider()
        val today = Instant.fromEpochMilliseconds(nowMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
        return today.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    }
}
