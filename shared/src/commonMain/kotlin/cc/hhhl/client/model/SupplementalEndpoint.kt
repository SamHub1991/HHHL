package cc.hhhl.client.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.JsonElement

@Immutable
data class ActivityPubShowResult(
    val type: String,
    val objectJson: JsonElement,
)

@Immutable
data class ExternalResource(
    val type: String,
    val data: String,
)

@Immutable
data class RssFeed(
    val type: String,
    val id: String = "",
    val updated: String = "",
    val author: String = "",
    val link: String = "",
    val title: String = "",
    val description: String = "",
    val items: List<RssFeedItem> = emptyList(),
)

@Immutable
data class RssFeedItem(
    val link: String = "",
    val guid: String = "",
    val title: String = "",
    val pubDate: String = "",
    val description: String = "",
    val media: List<RssFeedMedia> = emptyList(),
)

@Immutable
data class RssFeedMedia(
    val medium: String = "",
    val url: String = "",
    val type: String = "",
    val lang: String = "",
)

@Immutable
data class RetentionRecord(
    val createdAt: String,
    val users: Double,
    val data: Map<String, Double>,
)

@Immutable
data class Sponsor(
    val name: String,
    val imageUrl: String? = null,
    val websiteUrl: String? = null,
    val profile: String,
)

@Immutable
data class DriveUsage(
    val capacity: Double,
    val usage: Double,
)
