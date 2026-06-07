package cc.hhhl.client.automation

import cc.hhhl.client.api.ComposeDraft
import cc.hhhl.client.api.DriveFileUpload
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.NoteVisibility
import cc.hhhl.client.repository.DriveFileRepository
import cc.hhhl.client.repository.DriveFileRepositoryResult
import cc.hhhl.client.repository.ChatMessageRepositoryResult
import cc.hhhl.client.repository.ChatRepository
import cc.hhhl.client.repository.ComposeRepository
import cc.hhhl.client.repository.ComposeRepositoryResult
import cc.hhhl.client.repository.NoteActionRepository
import cc.hhhl.client.repository.NoteActionRepositoryResult
import cc.hhhl.client.repository.NoteActionRequest
import cc.hhhl.client.repository.NotificationRepository
import cc.hhhl.client.repository.NotificationRepositoryResult
import cc.hhhl.client.ai.AiBridge
import cc.hhhl.client.ai.AiBridgeImageResult
import cc.hhhl.client.ai.AiBridgeResult
import cc.hhhl.client.ai.AiGeneratedImage
import cc.hhhl.client.ai.AiImageRequestOptions
import cc.hhhl.client.ai.NoopAiBridge
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

class AppAutomationActionExecutor(
    private val chatRepository: ChatRepository,
    private val notificationRepository: NotificationRepository,
    private val composeRepository: ComposeRepository? = null,
    private val noteActionRepository: NoteActionRepository? = null,
    private val driveFileRepository: DriveFileRepository? = null,
    private val clipboardWriter: ((String) -> Boolean?)? = null,
    private val systemNotificationPublisher: ((String, String) -> Boolean?)? = null,
    private val aiBridge: AiBridge = NoopAiBridge,
    private val aiGeneratedChatMessageReporter: ((ChatMessage) -> Unit)? = null,
    private val aiGeneratedNoteReporter: ((String) -> Unit)? = null,
    private val attachmentAuthHeaderProvider: () -> Map<String, String> = { emptyMap() },
    private val httpClient: HttpClient = defaultAutomationHttpClient(),
) : AutomationActionExecutor {
    override suspend fun execute(
        action: AutomationAction,
        event: AutomationEvent,
        title: String,
        body: String,
    ): AutomationActionExecutionResult {
        return when (action.type) {
            AutomationActionType.AddLog -> AutomationActionExecutionResult(true, body)
            AutomationActionType.SystemNotification -> createNotification(title, body)
            AutomationActionType.ForwardToRoom -> forwardToRoom(action.targetId, body)
            AutomationActionType.AiForwardToRoom -> generateAndForwardToRoom(action, event)
            AutomationActionType.ForwardToUser -> forwardToUser(action.targetId, body)
            AutomationActionType.ReplyToChat -> replyToChat(action, event, body)
            AutomationActionType.AiReplyToChat -> generateAndReplyToChat(action, event)
            AutomationActionType.ReplyToNote -> replyToNote(action, event, body)
            AutomationActionType.AiReplyToNote -> generateAndReplyToNote(action, event)
            AutomationActionType.QuoteNote -> quoteNote(action, event, body)
            AutomationActionType.AiQuoteNote -> generateAndQuoteNote(action, event)
            AutomationActionType.RenoteNote -> renoteNote(action, event)
            AutomationActionType.PostToChannel -> postToChannel(action, event, body)
            AutomationActionType.CopyChannelLink -> copyChannelLink(action, event)
            AutomationActionType.Webhook -> sendWebhook(action.targetId, event, title, body)
            AutomationActionType.AiGenerateLog -> generateAiLog(action.bodyTemplate, event)
            AutomationActionType.AiGenerateNotification -> generateAiNotification(title, action.bodyTemplate, event)
            AutomationActionType.AiGenerateWebhook -> generateAiWebhook(action.targetId, action.bodyTemplate, event, title)
        }
    }

    private suspend fun generateAndForwardToRoom(
        action: AutomationAction,
        event: AutomationEvent,
    ): AutomationActionExecutionResult {
        return when (val result = generateOutgoingText(action.bodyTemplate, event, "聊天室转发内容")) {
            is AiBridgeResult.Success -> {
                val generated = result.text.cleanedOutgoingText()
                if (generated.shouldSkipGeneratedAction()) {
                    AutomationActionExecutionResult(true, "AI 判断无需转发")
                } else {
                    forwardToRoom(action.targetId, generated, aiGenerated = true)
                }
            }
            is AiBridgeResult.Error -> AutomationActionExecutionResult(false, result.message)
        }
    }

    private suspend fun generateAndReplyToChat(
        action: AutomationAction,
        event: AutomationEvent,
    ): AutomationActionExecutionResult {
        return when (val result = generateOutgoingText(action.bodyTemplate, event, "聊天回复")) {
            is AiBridgeResult.Success -> {
                val generated = result.text.cleanedOutgoingText()
                if (generated.shouldSkipGeneratedAction()) {
                    AutomationActionExecutionResult(true, "AI 判断无需回复")
                } else if (generated.isAutomationImageEditPrompt()) {
                    editImageAndReplyToChat(action, event, generated.automationImageEditRequest())
                } else if (generated.isAutomationImagePrompt()) {
                    generateImageAndReplyToChat(action, event, generated.automationImageRequest())
                } else {
                    replyToChat(action, event, generated, aiGenerated = true)
                }
            }
            is AiBridgeResult.Error -> AutomationActionExecutionResult(false, result.message)
        }
    }

    private suspend fun generateAndReplyToNote(
        action: AutomationAction,
        event: AutomationEvent,
    ): AutomationActionExecutionResult {
        return when (val result = generateOutgoingText(action.bodyTemplate, event, "帖子回复")) {
            is AiBridgeResult.Success -> {
                val generated = result.text.cleanedOutgoingText()
                if (generated.shouldSkipGeneratedAction()) {
                    AutomationActionExecutionResult(true, "AI 判断无需回复帖子")
                } else {
                    replyToNote(action, event, generated, aiGenerated = true)
                }
            }
            is AiBridgeResult.Error -> AutomationActionExecutionResult(false, result.message)
        }
    }

    private suspend fun generateAndQuoteNote(
        action: AutomationAction,
        event: AutomationEvent,
    ): AutomationActionExecutionResult {
        return when (val result = generateOutgoingText(action.bodyTemplate, event, "引用帖子")) {
            is AiBridgeResult.Success -> {
                val generated = result.text.cleanedOutgoingText()
                if (generated.shouldSkipGeneratedAction()) {
                    AutomationActionExecutionResult(true, "AI 判断无需引用")
                } else {
                    quoteNote(action, event, generated, aiGenerated = true)
                }
            }
            is AiBridgeResult.Error -> AutomationActionExecutionResult(false, result.message)
        }
    }

    private suspend fun generateOutgoingText(
        prompt: String,
        event: AutomationEvent,
        actionLabel: String,
    ): AiBridgeResult {
        val fullPrompt = buildString {
            appendLine(prompt.ifBlank { "根据事件生成自然、克制、贴合上下文的$actionLabel。" })
            appendLine("只输出要发送的正文，不要解释，不要加标题。")
            if (actionLabel == "聊天回复") {
                appendLine("如果对方明确要求画图、生成图片、生图、出图或把描述变成图片，第一行只输出 IMAGE_PROMPT: 后接可直接用于生图模型的详细提示词，不要输出普通回复。")
                appendLine("如果对方明确要求编辑触发消息里的图片附件，第一行只输出 IMAGE_EDIT_PROMPT: 后接图生图编辑提示词，不要输出普通回复。")
                appendLine("如果能从语义判断出图片参数，优先在标记后输出一行 JSON：{\"prompt\":\"详细提示词\",\"size\":\"1024x1024|1024x1536|1536x1024|3840x2160|2160x3840\",\"quality\":\"low|medium|high|auto\",\"background\":\"opaque|auto\",\"output_format\":\"png|jpeg|webp\",\"output_compression\":0-100,\"n\":1-10,\"transparent\":true|false,\"caption\":\"可选聊天说明\"}。")
                appendLine("参数规则：头像/表情包/贴纸/透明/抠图倾向 png，transparent=true，并在 prompt 里要求透明背景或干净抠图；壁纸/横图倾向 1536x1024 或 3840x2160；竖屏/手机壁纸倾向 1024x1536 或 2160x3840；正方形头像/图标倾向 1024x1024；高清/精细用 high，未明确用 medium；多张候选从语义里取 n。")
            }
            appendLine("如果上下文不该自动执行，输出 SKIP。")
        }.trim()
        return aiBridge.generateAutomationText(
            prompt = fullPrompt,
            eventText = event.aiContextText(),
            fileIds = event.aiAttachmentFileIds(),
            fileContext = event.aiAttachmentContextText(),
        )
    }

    private suspend fun generateAiLog(
        prompt: String,
        event: AutomationEvent,
    ): AutomationActionExecutionResult {
        return when (val result = aiBridge.generateAutomationText(
            prompt = prompt,
            eventText = event.aiContextText(),
            fileIds = event.aiAttachmentFileIds(),
            fileContext = event.aiAttachmentContextText(),
        )) {
            is AiBridgeResult.Success -> AutomationActionExecutionResult(true, result.text)
            is AiBridgeResult.Error -> AutomationActionExecutionResult(false, result.message)
        }
    }

    private suspend fun generateAiNotification(
        title: String,
        prompt: String,
        event: AutomationEvent,
    ): AutomationActionExecutionResult {
        return when (val result = aiBridge.generateAutomationText(
            prompt = prompt,
            eventText = event.aiContextText(),
            fileIds = event.aiAttachmentFileIds(),
            fileContext = event.aiAttachmentContextText(),
        )) {
            is AiBridgeResult.Success -> createNotification(title, result.text)
            is AiBridgeResult.Error -> AutomationActionExecutionResult(false, result.message)
        }
    }

    private suspend fun generateAiWebhook(
        url: String,
        prompt: String,
        event: AutomationEvent,
        title: String,
    ): AutomationActionExecutionResult {
        return when (val result = aiBridge.generateAutomationText(
            prompt = prompt,
            eventText = event.aiContextText(),
            fileIds = event.aiAttachmentFileIds(),
            fileContext = event.aiAttachmentContextText(),
        )) {
            is AiBridgeResult.Success -> sendWebhook(url, event, title, result.text)
            is AiBridgeResult.Error -> AutomationActionExecutionResult(false, result.message)
        }
    }

    private suspend fun createNotification(
        title: String,
        body: String,
    ): AutomationActionExecutionResult {
        systemNotificationPublisher?.let { publisher ->
            val platformResult = runCatching {
                val published = publisher(title, body)
                published?.let {
                    AutomationActionExecutionResult(
                        success = it,
                        message = if (it) "已发送系统通知" else "系统通知权限未开启或通知发送失败",
                    )
                }
            }.getOrElse { error ->
                AutomationActionExecutionResult(false, error.message ?: "系统通知发送失败")
            }
            if (platformResult != null) return platformResult
        }
        return when (val result = notificationRepository.createNotification(body = body, header = title)) {
            NotificationRepositoryResult.ActionSuccess -> AutomationActionExecutionResult(true, "已发送系统通知")
            NotificationRepositoryResult.Unauthorized -> AutomationActionExecutionResult(false, "登录已失效，无法发送通知")
            is NotificationRepositoryResult.Error -> AutomationActionExecutionResult(false, result.message)
            NotificationRepositoryResult.AllRead,
            is NotificationRepositoryResult.Success,
                -> AutomationActionExecutionResult(true, "已发送系统通知")
        }
    }

    private suspend fun forwardToRoom(
        roomId: String,
        body: String,
        aiGenerated: Boolean = false,
    ): AutomationActionExecutionResult {
        val cleanRoomId = roomId.trim()
        if (cleanRoomId.isBlank()) return AutomationActionExecutionResult(false, "聊天室 ID 不能为空")
        return when (val result = chatRepository.sendMessage(roomId = cleanRoomId, text = body)) {
            is ChatMessageRepositoryResult.Created -> {
                if (aiGenerated) aiGeneratedChatMessageReporter?.invoke(result.message)
                AutomationActionExecutionResult(true, "已转发到聊天室")
            }
            ChatMessageRepositoryResult.Unauthorized -> AutomationActionExecutionResult(false, "登录已失效，无法转发")
            is ChatMessageRepositoryResult.Error -> AutomationActionExecutionResult(false, result.message)
            is ChatMessageRepositoryResult.Success,
            is ChatMessageRepositoryResult.Deleted,
            ChatMessageRepositoryResult.ReactionUpdated,
                -> AutomationActionExecutionResult(true, "已转发到聊天室")
        }
    }

    private suspend fun forwardToUser(
        userId: String,
        body: String,
        aiGenerated: Boolean = false,
    ): AutomationActionExecutionResult {
        val cleanUserId = userId.trim()
        if (cleanUserId.isBlank()) return AutomationActionExecutionResult(false, "用户 ID 不能为空")
        return when (val result = chatRepository.sendUserMessage(userId = cleanUserId, text = body)) {
            is ChatMessageRepositoryResult.Created -> {
                if (aiGenerated) aiGeneratedChatMessageReporter?.invoke(result.message)
                AutomationActionExecutionResult(true, "已转发给用户")
            }
            ChatMessageRepositoryResult.Unauthorized -> AutomationActionExecutionResult(false, "登录已失效，无法转发")
            is ChatMessageRepositoryResult.Error -> AutomationActionExecutionResult(false, result.message)
            is ChatMessageRepositoryResult.Success,
            is ChatMessageRepositoryResult.Deleted,
            ChatMessageRepositoryResult.ReactionUpdated,
                -> AutomationActionExecutionResult(true, "已转发给用户")
        }
    }

    private suspend fun replyToChat(
        action: AutomationAction,
        event: AutomationEvent,
        body: String,
        aiGenerated: Boolean = false,
    ): AutomationActionExecutionResult {
        val target = resolveChatTarget(action.targetId, event)
            ?: return AutomationActionExecutionResult(false, "找不到可回复的聊天会话")
        val message = body.withOptionalMention(event, action.mentionSender)
        if (message.isBlank()) return AutomationActionExecutionResult(false, "回复内容不能为空")
        val sourceMessageId = event.chatMessageId.takeIf { it.isNotBlank() }
        val replyId = sourceMessageId.takeIf { action.replyToEvent }
        val quoteId = sourceMessageId.takeIf { action.quoteEvent }
        return when (target) {
            is AutomationChatTarget.Room -> mapChatSendResult(
                chatRepository.sendMessage(
                    roomId = target.id,
                    text = message,
                    replyId = replyId,
                    quoteId = quoteId,
                ),
                successMessage = "已自动回复聊天室",
                unauthorizedMessage = "登录已失效，无法回复聊天室",
                aiGenerated = aiGenerated,
            )
            is AutomationChatTarget.User -> mapChatSendResult(
                chatRepository.sendUserMessage(
                    userId = target.id,
                    text = message,
                    replyId = replyId,
                    quoteId = quoteId,
                ),
                successMessage = "已自动回复私聊",
                unauthorizedMessage = "登录已失效，无法回复私聊",
                aiGenerated = aiGenerated,
            )
        }
    }

    private suspend fun generateImageAndReplyToChat(
        action: AutomationAction,
        event: AutomationEvent,
        imageRequest: AutomationImageRequest,
    ): AutomationActionExecutionResult {
        if (imageRequest.prompt.isBlank()) return AutomationActionExecutionResult(false, "AI 没有返回生图提示词")
        val image = when (
            val result = aiBridge.generateAutomationImage(
                prompt = imageRequest.effectivePrompt(),
                options = imageRequest.options,
            )
        ) {
            is AiBridgeImageResult.Success -> result
            is AiBridgeImageResult.Error -> return AutomationActionExecutionResult(false, result.message)
        }
        return uploadGeneratedImageAndReplyToChat(action, event, imageRequest, image)
    }

    private suspend fun editImageAndReplyToChat(
        action: AutomationAction,
        event: AutomationEvent,
        imageRequest: AutomationImageRequest,
    ): AutomationActionExecutionResult {
        if (imageRequest.prompt.isBlank()) return AutomationActionExecutionResult(false, "AI 没有返回图生图提示词")
        val sourceImage = when (val result = loadAutomationSourceImage(event)) {
            is AutomationSourceImageLoadResult.Success -> result
            is AutomationSourceImageLoadResult.Error -> return AutomationActionExecutionResult(false, result.message)
        }
        val image = when (
            val result = aiBridge.editAutomationImage(
                prompt = imageRequest.effectivePrompt(),
                imageBytes = sourceImage.bytes,
                imageContentType = sourceImage.contentType,
                imageFileName = sourceImage.fileName,
                options = imageRequest.options,
            )
        ) {
            is AiBridgeImageResult.Success -> result
            is AiBridgeImageResult.Error -> return AutomationActionExecutionResult(false, result.message)
        }
        return uploadGeneratedImageAndReplyToChat(action, event, imageRequest, image)
    }

    private suspend fun uploadGeneratedImageAndReplyToChat(
        action: AutomationAction,
        event: AutomationEvent,
        imageRequest: AutomationImageRequest,
        image: AiBridgeImageResult.Success,
    ): AutomationActionExecutionResult {
        val driveRepository = driveFileRepository
            ?: return AutomationActionExecutionResult(false, "Drive 上传执行器未接入，无法发送生成图片")
        val generatedImages = image.images.ifEmpty {
            listOf(AiGeneratedImage(image.bytes, image.contentType.ifBlank { "image/png" }, image.revisedPrompt))
        }
        val files = mutableListOf<cc.hhhl.client.model.DriveFile>()
        for (generatedImage in generatedImages) {
            val upload = DriveFileUpload(
                bytes = generatedImage.bytes,
                fileName = automationGeneratedImageFileName(generatedImage.contentType),
                contentType = generatedImage.contentType.ifBlank { "image/png" },
                comment = generatedImage.revisedPrompt
                    .ifBlank { imageRequest.prompt }
                    .take(AUTOMATION_GENERATED_IMAGE_COMMENT_MAX_LENGTH),
                force = true,
            )
            val file = when (val uploadResult = driveRepository.upload(upload)) {
                is DriveFileRepositoryResult.Success -> uploadResult.file
                DriveFileRepositoryResult.Unauthorized -> return AutomationActionExecutionResult(false, "登录已失效，无法上传生成图片")
                is DriveFileRepositoryResult.ValidationError -> return AutomationActionExecutionResult(false, uploadResult.message)
                is DriveFileRepositoryResult.Error -> return AutomationActionExecutionResult(false, uploadResult.message)
            }
            files += file
        }
        val target = resolveChatTarget(action.targetId, event)
            ?: return AutomationActionExecutionResult(false, "找不到可回复的聊天会话")
        val text = imageRequest.caption.withOptionalMention(event, action.mentionSender)
        val sourceMessageId = event.chatMessageId.takeIf { it.isNotBlank() }
        val replyId = sourceMessageId.takeIf { action.replyToEvent }
        val quoteId = sourceMessageId.takeIf { action.quoteEvent }
        val fileIds = files.map { it.id }.filter { it.isNotBlank() }
        return when (target) {
            is AutomationChatTarget.Room -> mapChatSendResult(
                chatRepository.sendMessage(
                    roomId = target.id,
                    text = text,
                    fileIds = fileIds,
                    replyId = replyId,
                    quoteId = quoteId,
                ),
                successMessage = "已生成并发送图片",
                unauthorizedMessage = "登录已失效，无法发送生成图片",
                aiGenerated = true,
            )
            is AutomationChatTarget.User -> mapChatSendResult(
                chatRepository.sendUserMessage(
                    userId = target.id,
                    text = text,
                    fileId = fileIds.firstOrNull(),
                    replyId = replyId,
                    quoteId = quoteId,
                ),
                successMessage = "已生成并发送图片",
                unauthorizedMessage = "登录已失效，无法发送生成图片",
                aiGenerated = true,
            )
        }
    }

    private suspend fun loadAutomationSourceImage(event: AutomationEvent): AutomationSourceImageLoadResult {
        val imageAttachments = event.attachments.filter { attachment ->
            attachment.type.startsWith("image/", ignoreCase = true)
        }
        val attachment = imageAttachments.firstOrNull { it.url.isNotBlank() || it.thumbnailUrl.isNotBlank() }
            ?: return if (imageAttachments.isEmpty()) {
                AutomationSourceImageLoadResult.Error("触发消息没有可编辑的图片附件")
            } else {
                AutomationSourceImageLoadResult.Error("图片附件没有可下载 URL，无法图生图")
            }
        val sourceUrl = attachment.url.ifBlank { attachment.thumbnailUrl }.trim()
        return runCatching {
            val response = httpClient.get(sourceUrl) {
                header(HttpHeaders.Accept, attachment.type.ifBlank { "image/*" })
                if (sourceUrl.canReceiveAttachmentAuthHeaders()) {
                    attachmentAuthHeaderProvider().cleanAttachmentAuthHeaders().forEach { (name, value) ->
                        header(name, value)
                    }
                }
            }
            if (response.status.value !in 200..299) {
                return@runCatching AutomationSourceImageLoadResult.Error("源图片下载失败：HTTP ${response.status.value}")
            }
            val bytes = response.bodyAsBytes()
            if (bytes.isEmpty()) {
                AutomationSourceImageLoadResult.Error("源图片下载为空，无法图生图")
            } else {
                AutomationSourceImageLoadResult.Success(
                    bytes = bytes,
                    contentType = response.headers[HttpHeaders.ContentType]
                        ?.substringBefore(';')
                        ?.trim()
                        ?.ifBlank { null }
                        ?: attachment.type.ifBlank { "image/png" },
                    fileName = attachment.sourceImageFileName(),
                )
            }
        }.getOrElse { error ->
            AutomationSourceImageLoadResult.Error(error.message ?: "源图片下载失败")
        }
    }

    private suspend fun replyToNote(
        action: AutomationAction,
        event: AutomationEvent,
        body: String,
        aiGenerated: Boolean = false,
    ): AutomationActionExecutionResult {
        val noteId = action.targetId.trim().ifBlank { event.noteId }.takeIf { it.isNotBlank() }
            ?: return AutomationActionExecutionResult(false, "帖子 ID 不能为空")
        val message = body.withOptionalMention(event, action.mentionSender)
        if (message.isBlank()) return AutomationActionExecutionResult(false, "回复内容不能为空")
        return sendComposeDraft(
            ComposeDraft(
                text = message,
                replyId = noteId,
                visibility = NoteVisibility.Public,
            ),
            successMessage = "已回复帖子",
            aiGenerated = aiGenerated,
        )
    }

    private suspend fun quoteNote(
        action: AutomationAction,
        event: AutomationEvent,
        body: String,
        aiGenerated: Boolean = false,
    ): AutomationActionExecutionResult {
        val noteId = action.targetId.trim().ifBlank { event.noteId }.takeIf { it.isNotBlank() }
            ?: return AutomationActionExecutionResult(false, "帖子 ID 不能为空")
        val message = body.withOptionalMention(event, action.mentionSender)
        if (message.isBlank()) return AutomationActionExecutionResult(false, "引用正文不能为空")
        return sendComposeDraft(
            ComposeDraft(
                text = message,
                renoteId = noteId,
                visibility = NoteVisibility.Public,
            ),
            successMessage = "已引用帖子",
            aiGenerated = aiGenerated,
        )
    }

    private suspend fun renoteNote(
        action: AutomationAction,
        event: AutomationEvent,
    ): AutomationActionExecutionResult {
        val noteId = action.targetId.trim().ifBlank { event.noteId }.takeIf { it.isNotBlank() }
            ?: return AutomationActionExecutionResult(false, "帖子 ID 不能为空")
        val repository = noteActionRepository
            ?: return AutomationActionExecutionResult(false, "帖子操作执行器未接入")
        return when (val result = repository.perform(NoteActionRequest.Renote(noteId))) {
            is NoteActionRepositoryResult.Success -> AutomationActionExecutionResult(true, result.message)
            NoteActionRepositoryResult.Unauthorized -> AutomationActionExecutionResult(false, "登录已失效，无法转发帖子")
            is NoteActionRepositoryResult.Error -> AutomationActionExecutionResult(false, result.message)
        }
    }

    private suspend fun postToChannel(
        action: AutomationAction,
        event: AutomationEvent,
        body: String,
    ): AutomationActionExecutionResult {
        val channelId = action.targetId.trim().ifBlank { event.channelId }.takeIf { it.isNotBlank() }
            ?: return AutomationActionExecutionResult(false, "频道 ID 不能为空")
        val message = body.withOptionalMention(event, action.mentionSender)
        if (message.isBlank()) return AutomationActionExecutionResult(false, "频道帖子正文不能为空")
        return sendComposeDraft(
            ComposeDraft(
                text = message,
                channelId = channelId,
                visibility = NoteVisibility.Public,
            ),
            successMessage = "已发布到频道",
        )
    }

    private fun copyChannelLink(
        action: AutomationAction,
        event: AutomationEvent,
    ): AutomationActionExecutionResult {
        val link = channelLink(action.targetId, event)
            ?: return AutomationActionExecutionResult(false, "频道 ID 或链接不能为空")
        val writer = clipboardWriter
            ?: return AutomationActionExecutionResult(false, "当前运行环境未接入剪贴板，频道链接：$link")
        return runCatching {
            val written = writer(link) ?: true
            AutomationActionExecutionResult(
                success = written,
                message = if (written) "已复制频道链接" else "剪贴板写入失败：$link",
            )
        }.getOrElse { error ->
            AutomationActionExecutionResult(false, error.message ?: "剪贴板写入失败：$link")
        }
    }

    private suspend fun sendComposeDraft(
        draft: ComposeDraft,
        successMessage: String,
        aiGenerated: Boolean = false,
    ): AutomationActionExecutionResult {
        val repository = composeRepository
            ?: return AutomationActionExecutionResult(false, "发帖执行器未接入")
        return when (val result = repository.send(draft)) {
            is ComposeRepositoryResult.Success -> {
                if (aiGenerated) result.createdNoteId?.let { aiGeneratedNoteReporter?.invoke(it) }
                AutomationActionExecutionResult(true, successMessage)
            }
            ComposeRepositoryResult.Unauthorized -> AutomationActionExecutionResult(false, "登录已失效，无法发帖")
            is ComposeRepositoryResult.ValidationError -> AutomationActionExecutionResult(false, result.message)
            is ComposeRepositoryResult.Error -> AutomationActionExecutionResult(false, result.message)
        }
    }

    private fun mapChatSendResult(
        result: ChatMessageRepositoryResult,
        successMessage: String,
        unauthorizedMessage: String,
        aiGenerated: Boolean = false,
    ): AutomationActionExecutionResult {
        return when (result) {
            is ChatMessageRepositoryResult.Created -> {
                if (aiGenerated) aiGeneratedChatMessageReporter?.invoke(result.message)
                AutomationActionExecutionResult(true, successMessage)
            }
            ChatMessageRepositoryResult.Unauthorized -> AutomationActionExecutionResult(false, unauthorizedMessage)
            is ChatMessageRepositoryResult.Error -> AutomationActionExecutionResult(false, result.message)
            is ChatMessageRepositoryResult.Success,
            is ChatMessageRepositoryResult.Deleted,
            ChatMessageRepositoryResult.ReactionUpdated,
                -> AutomationActionExecutionResult(true, successMessage)
        }
    }

    private suspend fun sendWebhook(
        url: String,
        event: AutomationEvent,
        title: String,
        body: String,
    ): AutomationActionExecutionResult {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("https://") && !cleanUrl.startsWith("http://")) {
            return AutomationActionExecutionResult(false, "Webhook URL 必须以 http:// 或 https:// 开头")
        }
        return runCatching {
            val response = httpClient.post(cleanUrl) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                setBody(
                    AutomationWebhookPayload.from(
                        event = event,
                        title = title,
                        body = body,
                        attachmentAuthHeaders = if (event.attachments.any { it.url.isNotBlank() } && cleanUrl.canReceiveAttachmentAuthHeaders()) {
                            attachmentAuthHeaderProvider().cleanAttachmentAuthHeaders()
                        } else {
                            emptyMap()
                        },
                    ),
                )
            }
            if (response.status.value in 200..299) {
                AutomationActionExecutionResult(true, "Webhook 已发送 (${response.status.value})")
            } else {
                val responseBody = runCatching { response.body<String>() }.getOrDefault("").take(160)
                AutomationActionExecutionResult(
                    success = false,
                    message = "Webhook 返回 ${response.status.value}${responseBody.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()}",
                )
            }
        }.getOrElse { error ->
            AutomationActionExecutionResult(false, error.webhookFailureMessage())
        }
    }
}

@Serializable
private data class AutomationWebhookPayload(
    val title: String,
    val body: String,
    val eventId: String,
    val eventKind: String,
    val eventLabel: String,
    val chatMessageId: String,
    val sourceKind: String,
    val senderUserId: String,
    val senderUsername: String,
    val senderHost: String,
    val senderName: String,
    val roomId: String,
    val roomName: String,
    val directUserId: String,
    val messageText: String,
    val messageType: String,
    val attachmentCount: Int,
    val attachments: List<AutomationAttachment>,
    val attentionKind: String,
    val notificationType: String,
    val notificationText: String,
    val noteId: String,
    val channelId: String,
    val channelName: String,
    val timelineKind: String,
    val noteVisibility: String,
    val createdAt: String,
    val attachmentAuthHeaders: Map<String, String>,
    val variables: JsonObject,
) {
    companion object {
        fun from(
            event: AutomationEvent,
            title: String,
            body: String,
            attachmentAuthHeaders: Map<String, String> = emptyMap(),
        ): AutomationWebhookPayload {
            val variables = listOf(
                "event.id",
                "event.kind",
                "event.label",
                "message.id",
                "source.kind",
                "sender.id",
                "sender.username",
                "sender.host",
                "sender.name",
                "sender.mention",
                "room.id",
                "room.name",
                "direct.user.id",
                "message.text",
                "message.type",
                "attachment.count",
                "attachment.id",
                "attachment.name",
                "attachment.type",
                "attachment.url",
                "attachment.thumbnailUrl",
                "attachment.description",
                "attachment.size",
                "attachment.isSensitive",
                "attention.kind",
                "notification.type",
                "notification.text",
                "note.id",
                "note.link",
                "channel.id",
                "channel.name",
                "channel.link",
                "timeline.kind",
                "note.visibility",
                "createdAt",
            ).associateWith { name -> JsonPrimitive(event.variable(name)) }
            return AutomationWebhookPayload(
                title = title,
                body = body,
                eventId = event.id,
                eventKind = event.trigger.name,
                eventLabel = event.displayLabel,
                chatMessageId = event.chatMessageId,
                sourceKind = event.sourceKind,
                senderUserId = event.senderUserId,
                senderUsername = event.senderUsername,
                senderHost = event.senderHost,
                senderName = event.senderName,
                roomId = event.roomId,
                roomName = event.roomName,
                directUserId = event.directUserId,
                messageText = event.messageText,
                messageType = event.messageType,
                attachmentCount = event.attachments.size,
                attachments = event.attachments,
                attentionKind = event.attentionKind,
                notificationType = event.notificationType,
                notificationText = event.notificationText,
                noteId = event.noteId,
                channelId = event.channelId,
                channelName = event.channelName,
                timelineKind = event.timelineKind,
                noteVisibility = event.noteVisibility,
                createdAt = event.createdAtLabel,
                attachmentAuthHeaders = attachmentAuthHeaders,
                variables = JsonObject(variables),
            )
        }
    }
}

private sealed interface AutomationChatTarget {
    val id: String

    data class Room(override val id: String) : AutomationChatTarget
    data class User(override val id: String) : AutomationChatTarget
}

private fun resolveChatTarget(targetId: String, event: AutomationEvent): AutomationChatTarget? {
    val cleanTarget = targetId.trim()
    if (cleanTarget.startsWith("room:", ignoreCase = true)) {
        return cleanTarget.substringAfter(':').trim().takeIf { it.isNotBlank() }?.let { AutomationChatTarget.Room(it) }
    }
    if (cleanTarget.startsWith("user:", ignoreCase = true)) {
        return cleanTarget.substringAfter(':').trim().takeIf { it.isNotBlank() }?.let { AutomationChatTarget.User(it) }
    }
    if (cleanTarget.isNotBlank()) {
        return if (event.sourceKind == "direct") AutomationChatTarget.User(cleanTarget) else AutomationChatTarget.Room(cleanTarget)
    }
    return event.directUserId.takeIf { it.isNotBlank() }?.let { AutomationChatTarget.User(it) }
        ?: event.roomId.takeIf { it.isNotBlank() }?.let { AutomationChatTarget.Room(it) }
}

private fun String.withOptionalMention(event: AutomationEvent, mentionSender: Boolean): String {
    val clean = trim()
    val mention = event.senderMention.takeIf { mentionSender && it.isNotBlank() } ?: return clean
    return if (clean.startsWith(mention)) clean else "$mention $clean".trim()
}

private fun String.cleanedOutgoingText(): String {
    return trim()
        .removeSurrounding("\"")
        .removeSurrounding("'")
        .take(MAX_AUTOMATION_OUTGOING_TEXT)
}

private fun String.shouldSkipGeneratedAction(): Boolean {
    val clean = trim().uppercase()
    return clean == "SKIP" || clean == "NO_REPLY" || clean == "NO" || trim() == "不回复" || trim() == "不执行"
}

private fun String.isAutomationImagePrompt(): Boolean {
    val clean = trim()
    return clean.startsWith("IMAGE_PROMPT:", ignoreCase = true) ||
        clean.startsWith("IMAGE:", ignoreCase = true) ||
        clean.startsWith("生图：")
}

private fun String.isAutomationImageEditPrompt(): Boolean {
    val clean = trim()
    return clean.startsWith("IMAGE_EDIT_PROMPT:", ignoreCase = true) ||
        clean.startsWith("EDIT_IMAGE:", ignoreCase = true) ||
        clean.startsWith("IMAGE_EDIT:", ignoreCase = true) ||
        clean.startsWith("图生图：") ||
        clean.startsWith("编辑图片：") ||
        clean.startsWith("改图：")
}

private fun String.automationImagePrompt(): String {
    val clean = trim()
    return when {
        clean.startsWith("IMAGE_PROMPT:", ignoreCase = true) -> clean.substringAfter(':')
        clean.startsWith("IMAGE:", ignoreCase = true) -> clean.substringAfter(':')
        clean.startsWith("生图：") -> clean.substringAfter('：')
        else -> clean
    }.trim()
}

private fun String.automationImageRequest(): AutomationImageRequest {
    return automationImagePrompt().toAutomationImageRequest()
}

private fun String.automationImageEditPrompt(): String {
    val clean = trim()
    return when {
        clean.startsWith("IMAGE_EDIT_PROMPT:", ignoreCase = true) -> clean.substringAfter(':')
        clean.startsWith("EDIT_IMAGE:", ignoreCase = true) -> clean.substringAfter(':')
        clean.startsWith("IMAGE_EDIT:", ignoreCase = true) -> clean.substringAfter(':')
        clean.startsWith("图生图：") -> clean.substringAfter('：')
        clean.startsWith("编辑图片：") -> clean.substringAfter('：')
        clean.startsWith("改图：") -> clean.substringAfter('：')
        else -> clean
    }.trim()
}

private fun String.automationImageEditRequest(): AutomationImageRequest {
    return automationImageEditPrompt().toAutomationImageRequest()
}

private fun String.toAutomationImageRequest(): AutomationImageRequest {
    val payload = trim()
    val json = payload.parseAutomationImageJsonObject()
    if (json == null) {
        return AutomationImageRequest(prompt = payload.take(MAX_AUTOMATION_IMAGE_PROMPT_LENGTH))
    }
    val prompt = json.stringValue("prompt", "image_prompt", "imagePrompt", "提示词")
        .ifBlank { payload }
        .take(MAX_AUTOMATION_IMAGE_PROMPT_LENGTH)
    val transparent = json.booleanValue("transparent", "透明", "transparent_background") ||
        json.stringValue("background", "背景").equals("transparent", ignoreCase = true) ||
        json.stringValue("background", "背景") == "透明"
    val outputFormat = json.stringValue("output_format", "outputFormat", "format", "格式")
        .ifBlank { if (transparent) "png" else "png" }
    val background = json.stringValue("background", "背景")
        .takeIf { it.isNotBlank() && !it.equals("transparent", ignoreCase = true) && it != "透明" }
        ?: if (transparent) "auto" else null
    return AutomationImageRequest(
        prompt = prompt,
        options = AiImageRequestOptions(
            size = json.stringValue("size", "resolution", "preset", "分辨率", "尺寸").ifBlank { "1024x1024" },
            quality = json.stringValue("quality", "质量", "清晰度").ifBlank { "medium" },
            background = background,
            outputFormat = outputFormat,
            outputCompression = json.intValue("output_compression", "outputCompression", "compression", "压缩"),
            count = json.intValue("n", "count", "数量", "张数") ?: 1,
        ),
        caption = json.stringValue("caption", "message", "text", "说明", "回复")
            .take(MAX_AUTOMATION_IMAGE_CAPTION_LENGTH),
        transparent = transparent,
    )
}

private fun String.parseAutomationImageJsonObject(): JsonObject? {
    val clean = trim()
    if (!clean.startsWith("{")) return null
    return runCatching {
        AutomationProtocolJson.parseToJsonElement(clean).jsonObject
    }.getOrNull()
}

private fun JsonObject.stringValue(vararg keys: String): String {
    return keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
    }.orEmpty()
}

private fun JsonObject.intValue(vararg keys: String): Int? {
    return keys.firstNotNullOfOrNull { key ->
        val primitive = this[key] as? JsonPrimitive ?: return@firstNotNullOfOrNull null
        primitive.intOrNull ?: primitive.contentOrNull?.trim()?.toIntOrNull()
    }
}

private fun JsonObject.booleanValue(vararg keys: String): Boolean {
    return keys.firstNotNullOfOrNull { key ->
        val primitive = this[key] as? JsonPrimitive ?: return@firstNotNullOfOrNull null
        primitive.booleanOrNull ?: when (primitive.contentOrNull?.trim()?.lowercase()) {
            "true", "yes", "1", "是", "需要", "透明" -> true
            "false", "no", "0", "否", "不需要", "不透明" -> false
            else -> null
        }
    } ?: false
}

private fun AutomationImageRequest.effectivePrompt(): String {
    if (!transparent) return prompt
    val lower = prompt.lowercase()
    val alreadyMentionsTransparency = lower.contains("transparent") ||
        prompt.contains("透明") ||
        prompt.contains("抠图")
    if (alreadyMentionsTransparency) return prompt
    return "$prompt。生成透明背景图片，主体边缘清晰，适合抠图使用，不要水印。"
}

private fun AutomationAttachment.sourceImageFileName(): String {
    val cleanName = name.trim().takeIf { it.isNotBlank() }
        ?: id.trim().takeIf { it.isNotBlank() }
        ?: "source"
    val extension = cleanName.substringAfterLast('.', missingDelimiterValue = "")
        .takeIf { it.length in 2..5 }
        ?: when (type.lowercase().substringBefore(';').trim()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            else -> "png"
        }
    return if (cleanName.endsWith(".$extension", ignoreCase = true)) cleanName else "$cleanName.$extension"
}

private fun automationGeneratedImageFileName(contentType: String): String {
    val extension = when (contentType.lowercase().substringBefore(';').trim()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/webp" -> "webp"
        else -> "png"
    }
    return "hhhl-ai-image-${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}.$extension"
}

private fun channelLink(targetId: String, event: AutomationEvent): String? {
    val clean = targetId.trim().ifBlank { event.channelId }
    if (clean.isBlank()) return null
    if (clean.startsWith("https://", ignoreCase = true) || clean.startsWith("http://", ignoreCase = true)) return clean
    return "$DEFAULT_LOCAL_BASE_URL/channels/$clean"
}

private fun String.canReceiveAttachmentAuthHeaders(): Boolean {
    val clean = trim()
    val lower = clean.lowercase()
    val host = lower
        .removePrefix("http://")
        .removePrefix("https://")
        .substringBefore('/')
        .substringBefore(':')
    val explicitLocalAttachmentAuth = lower.substringAfter('?', "")
        .split('&')
        .any { parameter ->
            parameter == "hhhlattachmentauth=1" ||
                parameter == "hhhl_attachment_auth=1" ||
                parameter == "attachmentauth=1" ||
                parameter == "attachment_auth=1"
        }
    return host == "localhost" ||
        host == "127.0.0.1" ||
        host == "10.0.2.2" ||
        (explicitLocalAttachmentAuth && host.isPrivateNetworkHost())
}

private fun String.isPrivateNetworkHost(): Boolean {
    return startsWith("10.") ||
        startsWith("192.168.") ||
        private172HostPattern.matches(this)
}

private fun Map<String, String>.cleanAttachmentAuthHeaders(): Map<String, String> {
    return buildMap {
        val authorization = this@cleanAttachmentAuthHeaders["Authorization"]
            ?: this@cleanAttachmentAuthHeaders["authorization"]
        if (!authorization.isNullOrBlank()) put(HttpHeaders.Authorization, authorization.trim())
    }
}

private const val DEFAULT_LOCAL_BASE_URL = "https://dc.hhhl.cc"
private const val MAX_AUTOMATION_OUTGOING_TEXT = 1600
private const val MAX_AUTOMATION_IMAGE_PROMPT_LENGTH = 2000
private const val MAX_AUTOMATION_IMAGE_CAPTION_LENGTH = 300
private const val AUTOMATION_GENERATED_IMAGE_COMMENT_MAX_LENGTH = 500
private const val AUTOMATION_WEBHOOK_REQUEST_TIMEOUT_MS = 120_000L
private const val AUTOMATION_WEBHOOK_CONNECT_TIMEOUT_MS = 8_000L
private val private172HostPattern = Regex("""172\.(1[6-9]|2\d|3[0-1])\..+""")
private val AutomationProtocolJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private data class AutomationImageRequest(
    val prompt: String,
    val options: AiImageRequestOptions = AiImageRequestOptions(),
    val caption: String = "",
    val transparent: Boolean = false,
)

private sealed interface AutomationSourceImageLoadResult {
    data class Success(
        val bytes: ByteArray,
        val contentType: String,
        val fileName: String,
    ) : AutomationSourceImageLoadResult

    data class Error(val message: String) : AutomationSourceImageLoadResult
}

private fun Throwable.webhookFailureMessage(): String {
    val raw = message.orEmpty()
    return when {
        raw.contains("Request timeout", ignoreCase = true) ||
            raw.contains("request_timeout", ignoreCase = true) ->
            "Webhook 接收端响应超时，已等待 ${AUTOMATION_WEBHOOK_REQUEST_TIMEOUT_MS / 1000} 秒。接收服务可能还在处理图片识别、key 验证或重启 WatchApi：$raw"
        raw.contains("unexpected end of stream", ignoreCase = true) ->
            "Webhook 连接被接收端中途关闭。通常是本机接收脚本被重启、多开抢占端口，或处理过程中进程退出：$raw"
        raw.isNotBlank() -> raw
        else -> "Webhook 发送失败"
    }
}

private fun defaultAutomationHttpClient(): HttpClient {
    return HttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = AUTOMATION_WEBHOOK_REQUEST_TIMEOUT_MS
            connectTimeoutMillis = AUTOMATION_WEBHOOK_CONNECT_TIMEOUT_MS
            socketTimeoutMillis = AUTOMATION_WEBHOOK_REQUEST_TIMEOUT_MS
        }
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                    encodeDefaults = true
                },
            )
        }
    }
}
