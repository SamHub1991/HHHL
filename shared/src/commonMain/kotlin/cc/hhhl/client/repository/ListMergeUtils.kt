package cc.hhhl.client.repository

internal inline fun <T, K> List<T>.appendDistinctBy(
    incoming: List<T>,
    keySelector: (T) -> K,
): List<T> {
    if (incoming.isEmpty()) return this

    val seen = HashSet<K>(size + incoming.size)
    val merged = ArrayList<T>(size + incoming.size)
    var changed = false

    for (item in this) {
        if (seen.add(keySelector(item))) {
            merged.add(item)
        } else {
            changed = true
        }
    }
    for (item in incoming) {
        if (seen.add(keySelector(item))) {
            merged.add(item)
            changed = true
        }
    }

    return if (changed) merged else this
}

internal inline fun <T, K> List<T>.prependDistinctBy(
    incoming: List<T>,
    keySelector: (T) -> K,
): List<T> {
    if (incoming.isEmpty()) return this

    val seen = HashSet<K>(size + incoming.size)
    val merged = ArrayList<T>(size + incoming.size)
    var changed = false

    for (item in incoming) {
        val key = keySelector(item)
        if (seen.add(key)) {
            merged.add(item)
            changed = true
        }
    }
    for (item in this) {
        val key = keySelector(item)
        if (seen.add(key)) {
            merged.add(item)
        } else {
            changed = true
        }
    }

    return if (changed) merged else this
}

internal inline fun <T, K> List<T>.prependDistinctBy(
    item: T,
    keySelector: (T) -> K,
): List<T> {
    val itemKey = keySelector(item)
    var containsItem = false
    for (existing in this) {
        if (keySelector(existing) == itemKey) {
            containsItem = true
            break
        }
    }

    val merged = ArrayList<T>(size + if (containsItem) 0 else 1)
    merged.add(item)
    for (existing in this) {
        if (keySelector(existing) != itemKey) {
            merged.add(existing)
        }
    }
    return merged
}
