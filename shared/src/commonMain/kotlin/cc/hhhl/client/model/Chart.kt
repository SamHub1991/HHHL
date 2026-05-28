package cc.hhhl.client.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.JsonObject

enum class ChartSpan(val apiValue: String) {
    Day("day"),
    Hour("hour"),
}

enum class InstanceChartKind(val endpoint: List<String>) {
    ActiveUsers(listOf("charts", "active-users")),
    ActivityPubRequests(listOf("charts", "ap-request")),
    Drive(listOf("charts", "drive")),
    Federation(listOf("charts", "federation")),
    Instance(listOf("charts", "instance")),
    Notes(listOf("charts", "notes")),
    Users(listOf("charts", "users")),
}

enum class UserChartKind(val endpoint: List<String>) {
    Drive(listOf("charts", "user", "drive")),
    Following(listOf("charts", "user", "following")),
    Notes(listOf("charts", "user", "notes")),
    PageViews(listOf("charts", "user", "pv")),
    Reactions(listOf("charts", "user", "reactions")),
}

@Immutable
data class ChartPayload(
    val kind: String,
    val span: ChartSpan,
    val data: JsonObject,
)
