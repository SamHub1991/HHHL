package cc.hhhl.client.ui.component

import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.runtime.Composable

private val AiResultBulletRegex = Regex("""^\s*(?:[-*+]\s+|\d+[.)]\s+|[•·]\s*)""")
private val AiResultCheckboxRegex = Regex("""^\s*\[[ xX]\]\s+""")
private val AiResultNoisePrefixRegex = Regex("""^(?:静音词|过滤词|屏蔽词|关键词|建议|添加静音词|添加过滤词)\s*[:：]\s*""")
private val AiResultReasonTailRegex = Regex("""\s*[，,；;。.]?\s*(?:(?:原因|理由)\s*[:：]|因为).*$""")
private val AiResultQuotedCandidateRegex = Regex("""[`"'“”‘’「」『』]([^`"'“”‘’「」『』]{1,80})[`"'“”‘’「」『』]""")

@Composable
fun FlowRowScope.AiResultCommonActionChips(
    text: String,
    onCopyChecklist: ((String) -> Unit)? = null,
    onAddMutedWord: ((String) -> Unit)? = null,
    onCreateAutomationRule: ((String) -> Unit)? = null,
    onAddToWatchLater: (() -> Unit)? = null,
    onOpenRelatedNote: (() -> Unit)? = null,
) {
    onCopyChecklist?.let { copy ->
        HhhlActionChip(
            label = "复制为清单",
            onClick = { copy(text) },
        )
    }
    onAddToWatchLater?.let { add ->
        HhhlActionChip(label = "加入收藏", onClick = add)
    }
    onAddMutedWord?.let { add ->
        HhhlActionChip(
            label = "添加静音词",
            onClick = { add(text) },
        )
    }
    onCreateAutomationRule?.let { create ->
        HhhlActionChip(
            label = "创建自动化规则",
            onClick = { create(text) },
        )
    }
    onOpenRelatedNote?.let { open ->
        HhhlActionChip(label = "打开相关帖子", onClick = open)
    }
}

fun aiResultChecklistText(text: String): String {
    val lines = text
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { line ->
            line
                .replace(AiResultBulletRegex, "")
                .replace(AiResultCheckboxRegex, "")
                .trim()
        }
        .filter { it.isNotBlank() }
        .map { "- $it" }
        .toList()
    return lines.joinToString("\n")
}

fun aiResultMutedWordCandidate(text: String, maxLength: Int = 80): String? {
    val sourceLine = text
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        ?: return null
    val quoted = AiResultQuotedCandidateRegex.find(sourceLine)?.groupValues?.getOrNull(1)
    val raw = quoted ?: sourceLine
        .replace(AiResultBulletRegex, "")
        .replace(AiResultNoisePrefixRegex, "")
        .replace(AiResultReasonTailRegex, "")
    val candidate = raw
        .trim()
        .trim(' ', '\t', '，', ',', '。', '.', '；', ';', '：', ':', '-', '*', '“', '”', '"', '\'', '`', '「', '」', '『', '』')
        .take(maxLength)
        .trim()
    return candidate.takeIf { it.isNotBlank() }
}

fun aiResultReferencedNoteId(text: String, candidateNoteIds: Collection<String>): String? {
    val cleanText = text.trim()
    if (cleanText.isBlank()) return null
    return candidateNoteIds
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .sortedByDescending { it.length }
        .firstOrNull { noteId -> cleanText.contains(noteId) }
}
