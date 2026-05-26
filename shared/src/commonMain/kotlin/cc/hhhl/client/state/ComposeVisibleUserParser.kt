package cc.hhhl.client.state

fun String.toComposeVisibleUserTokens(): List<String> {
    return replace('，', ' ')
        .replace('、', ' ')
        .replace(';', ' ')
        .replace('；', ' ')
        .split(',', '\n', '\t', ' ')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
}

fun String.toComposeVisibleUserMention(): String? {
    val clean = trim().replace('＠', '@')
    if (!clean.startsWith("@")) return null
    return clean.removePrefix("@").trim().takeIf { it.isNotEmpty() }
}

fun String.isComposeVisibleUserMention(): Boolean {
    return toComposeVisibleUserMention() != null
}
