package cc.hhhl.client.automation

import cc.hhhl.client.model.Channel
import cc.hhhl.client.model.ChatRoom
import cc.hhhl.client.model.User

data class AutomationRuleDraftResolveInput(
    val rooms: List<ChatRoom> = emptyList(),
    val users: List<User> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val searchUsers: suspend (String) -> List<User> = { emptyList() },
    val loadRooms: suspend () -> List<ChatRoom> = { emptyList() },
    val loadChannels: suspend () -> List<Channel> = { emptyList() },
)

data class AutomationRuleDraftResolveResult(
    val rule: AutomationRule,
    val messages: List<String> = emptyList(),
)

suspend fun resolveAutomationRuleDraft(
    rule: AutomationRule,
    input: AutomationRuleDraftResolveInput,
): AutomationRuleDraftResolveResult {
    return AutomationRuleDraftResolver(input).resolve(rule)
}

private class AutomationRuleDraftResolver(
    private val input: AutomationRuleDraftResolveInput,
) {
    private val messages = mutableListOf<String>()
    private var loadedRooms: List<ChatRoom>? = null
    private var loadedChannels: List<Channel>? = null

    suspend fun resolve(rule: AutomationRule): AutomationRuleDraftResolveResult {
        val nextConditions = mutableListOf<AutomationCondition>()
        for (condition in rule.conditions) {
            nextConditions += resolveCondition(condition)
        }
        return AutomationRuleDraftResolveResult(
            rule = rule.copy(
                conditions = compactConditions(nextConditions),
                actions = rule.actions.map { action -> resolveAction(action) },
            ),
            messages = messages.distinct(),
        )
    }

    private suspend fun resolveCondition(condition: AutomationCondition): AutomationCondition {
        return when (condition.type) {
            AutomationConditionType.RoomNameContains -> resolveRoom(condition.value)?.let { room ->
                messages += "聊天室 ${room.name} -> ${room.id}"
                condition.copy(type = AutomationConditionType.RoomId, value = room.id)
            } ?: condition
            AutomationConditionType.SenderNameContains,
            AutomationConditionType.SenderUsername -> {
                if (condition.value.isAllUsersValue()) {
                    messages += "发送者范围：全部用户"
                    return condition.copy(value = "", enabled = false)
                }
                val users = resolveUsers(condition.value)
                if (users.isEmpty()) {
                    condition
                } else {
                    messages += "用户 ${users.joinToString("、") { it.displayName.ifBlank { it.username } }} -> ${users.joinToString(",") { it.id }}"
                    condition.copy(
                        type = if (users.size == 1) AutomationConditionType.SenderUserId else AutomationConditionType.SenderUserIds,
                        value = users.joinToString(",") { it.id },
                    )
                }
            }
            AutomationConditionType.ChannelNameContains -> resolveChannel(condition.value)?.let { channel ->
                messages += "频道 ${channel.name} -> ${channel.id}"
                condition.copy(type = AutomationConditionType.ChannelId, value = channel.id)
            } ?: condition
            else -> condition
        }
    }

    private suspend fun resolveAction(action: AutomationAction): AutomationAction {
        val cleanTarget = action.targetId.trim()
        return when (action.type) {
            AutomationActionType.ForwardToRoom -> resolveRoom(cleanTarget.removePrefixIgnoreCase("room:"))?.let { room ->
                messages += "动作聊天室 ${room.name} -> ${room.id}"
                action.copy(targetId = room.id)
            } ?: action.copy(targetId = cleanTarget.removePrefixIgnoreCase("room:"))
            AutomationActionType.ForwardToUser -> resolveUsers(cleanTarget.removePrefixIgnoreCase("user:")).singleOrNull()?.let { user ->
                messages += "动作用户 ${user.displayName.ifBlank { user.username }} -> ${user.id}"
                action.copy(targetId = user.id)
            } ?: action.copy(targetId = cleanTarget.removePrefixIgnoreCase("user:"))
            AutomationActionType.ReplyToChat,
            AutomationActionType.AiReplyToChat -> resolveReplyChatTarget(action, cleanTarget)
            AutomationActionType.PostToChannel,
            AutomationActionType.CopyChannelLink -> resolveChannel(cleanTarget.removePrefixIgnoreCase("channel:"))?.let { channel ->
                messages += "动作频道 ${channel.name} -> ${channel.id}"
                action.copy(targetId = channel.id)
            } ?: action.copy(targetId = cleanTarget.removePrefixIgnoreCase("channel:"))
            else -> action
        }
    }

    private suspend fun resolveReplyChatTarget(action: AutomationAction, cleanTarget: String): AutomationAction {
        if (cleanTarget.isBlank()) return action
        if (cleanTarget.startsWith("room:", ignoreCase = true)) {
            val room = resolveRoom(cleanTarget.substringAfter(':'))
            if (room != null) {
                messages += "回复聊天室 ${room.name} -> ${room.id}"
                return action.copy(targetId = "room:${room.id}")
            }
        }
        if (cleanTarget.startsWith("user:", ignoreCase = true)) {
            val user = resolveUsers(cleanTarget.substringAfter(':')).singleOrNull()
            if (user != null) {
                messages += "回复用户 ${user.displayName.ifBlank { user.username }} -> ${user.id}"
                return action.copy(targetId = "user:${user.id}")
            }
        }
        return action
    }

    private suspend fun resolveRoom(name: String): ChatRoom? {
        val clean = name.trim()
        if (clean.isBlank() || clean.looksLikeId()) return null
        val current = (input.rooms + loadedRooms.orEmpty()).distinctBy { it.id }
        current.uniqueByName(clean) { it.name }?.let { return it }
        val loaded = loadedRooms ?: input.loadRooms().also { loadedRooms = it }
        val remote = (current + loaded).distinctBy { it.id }.uniqueByName(clean) { it.name }
        if (remote == null) messages += "未能唯一解析聊天室：$clean"
        return remote
    }

    private suspend fun resolveChannel(name: String): Channel? {
        val clean = name.trim()
        if (clean.isBlank() || clean.looksLikeId()) return null
        val current = (input.channels + loadedChannels.orEmpty()).distinctBy { it.id }
        current.uniqueByName(clean) { it.name }?.let { return it }
        val loaded = loadedChannels ?: input.loadChannels().also { loadedChannels = it }
        val remote = (current + loaded).distinctBy { it.id }.uniqueByName(clean) { it.name }
        if (remote == null) messages += "未能唯一解析频道：$clean"
        return remote
    }

    private suspend fun resolveUsers(value: String): List<User> {
        val names = value.splitAutomationValues()
        if (names.isEmpty()) return emptyList()
        val resolved = mutableListOf<User>()
        for (name in names) {
            if (name.looksLikeId()) continue
            val user = input.users.uniqueUser(name) ?: input.searchUsers(name).uniqueUser(name)
            if (user == null) {
                messages += "未能唯一解析用户：$name"
            } else {
                resolved += user
            }
        }
        return resolved.distinctBy { it.id }
    }

    private fun compactConditions(conditions: List<AutomationCondition>): List<AutomationCondition> {
        val senderIdConditions = conditions.filter {
            it.type == AutomationConditionType.SenderUserId || it.type == AutomationConditionType.SenderUserIds
        }
        if (senderIdConditions.size <= 1) return conditions
        val mergedIds = senderIdConditions
            .flatMap { it.value.splitAutomationValues() }
            .distinct()
        val first = senderIdConditions.first()
        return conditions.filterNot {
            it.type == AutomationConditionType.SenderUserId || it.type == AutomationConditionType.SenderUserIds
        } + first.copy(type = AutomationConditionType.SenderUserIds, value = mergedIds.joinToString(","))
    }
}

private fun <T> List<T>.uniqueByName(name: String, selector: (T) -> String): T? {
    val clean = name.normalizedEntityName()
    if (clean.isBlank()) return null
    val exact = filter { selector(it).normalizedEntityName() == clean }
    if (exact.size == 1) return exact.single()
    val contains = filter { selector(it).normalizedEntityName().contains(clean) }
    return contains.singleOrNull()
}

private fun List<User>.uniqueUser(name: String): User? {
    val clean = name.normalizedEntityName().trimStart('@')
    if (clean.isBlank()) return null
    val exact = filter { user ->
        user.id.equals(clean, ignoreCase = true) ||
            user.username.equals(clean, ignoreCase = true) ||
            user.displayName.normalizedEntityName() == clean ||
            user.acct().equals(clean, ignoreCase = true) ||
            "@${user.acct()}".equals(clean, ignoreCase = true)
    }
    if (exact.size == 1) return exact.single()
    val contains = filter { user ->
        user.displayName.normalizedEntityName().contains(clean) ||
            user.username.normalizedEntityName().contains(clean)
    }
    return contains.singleOrNull()
}

private fun User.acct(): String {
    val cleanHost = host?.trim().orEmpty()
    return if (cleanHost.isBlank()) username else "$username@$cleanHost"
}

private fun String.normalizedEntityName(): String {
    return trim()
        .trim('"', '\'', '“', '”', '‘', '’', '「', '」', '『', '』', '《', '》')
        .lowercase()
}

private fun String.splitAutomationValues(): List<String> {
    return split(',', '，', '\n', ';', '；', '|', '/', '、')
        .map { it.trim().trim('"', '\'', '“', '”', '‘', '’') }
        .filter { it.isNotEmpty() }
}

private fun String.removePrefixIgnoreCase(prefix: String): String {
    return if (startsWith(prefix, ignoreCase = true)) substring(prefix.length).trim() else this
}

private fun String.looksLikeId(): Boolean {
    val clean = trim()
    if (clean.startsWith("room-", ignoreCase = true) || clean.startsWith("user-", ignoreCase = true) || clean.startsWith("channel-", ignoreCase = true)) return true
    if (clean.length < 12) return false
    return clean.all { it.isLetterOrDigit() || it == '-' || it == '_' }
}

private fun String.isAllUsersValue(): Boolean {
    return normalizedEntityName() in setOf("全部", "所有", "任意", "任何人", "全部用户", "所有用户", "all", "any", "anyone", "all users")
}
