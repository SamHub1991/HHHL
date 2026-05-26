package cc.hhhl.client.navigation

sealed interface SiteLinkNavigationTarget {
    data class NoteDetail(val noteId: String) : SiteLinkNavigationTarget
    data class Mention(val username: String) : SiteLinkNavigationTarget
    data class External(val url: String) : SiteLinkNavigationTarget
}

fun siteLinkNavigationTarget(url: String): SiteLinkNavigationTarget {
    val cleanUrl = url.trim()
    val path = localPath(cleanUrl)
        ?: return SiteLinkNavigationTarget.External(cleanUrl)

    return when {
        path.startsWith("notes/") -> {
            val noteId = path.removePrefix("notes/").substringBefore('/').trim()
            if (noteId.isNotBlank()) {
                SiteLinkNavigationTarget.NoteDetail(noteId)
            } else {
                SiteLinkNavigationTarget.External(cleanUrl)
            }
        }
        path.startsWith("@") -> {
            val username = path.removePrefix("@").substringBefore('/').trim()
            if (username.isNotBlank()) {
                SiteLinkNavigationTarget.Mention(username)
            } else {
                SiteLinkNavigationTarget.External(cleanUrl)
            }
        }
        else -> SiteLinkNavigationTarget.External(cleanUrl)
    }
}

private fun localPath(url: String): String? {
    val localUrlPath = firstMatchingLocalPrefix(url)?.let { prefix ->
        url.removePrefix(prefix)
    } ?: when {
        url.startsWith("/") && !url.startsWith("//") -> url.removePrefix("/")
        else -> return null
    }

    return localUrlPath
        .substringBefore('#')
        .substringBefore('?')
        .trim('/')
}

private fun firstMatchingLocalPrefix(url: String): String? {
    return localUrlPrefixes.firstOrNull { prefix ->
        url.startsWith(prefix)
    }
}

private val localUrlPrefixes = listOf(
    "https://dc.hhhl.cc/",
    "http://dc.hhhl.cc/",
)
