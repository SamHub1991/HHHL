package cc.hhhl.client.android

import android.content.Context
import cc.hhhl.client.state.RecentReactionStore

class AndroidRecentReactionStore(context: Context) : RecentReactionStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun loadRecentReactions(): List<String> {
        return preferences.getString(KEY_RECENT_REACTIONS, null)
            ?.split(SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
    }

    override fun saveRecentReactions(reactions: List<String>) {
        preferences.edit()
            .putString(KEY_RECENT_REACTIONS, reactions.joinToString(SEPARATOR))
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "hhhl_recent_reactions"
        const val KEY_RECENT_REACTIONS = "recent_reactions"
        const val SEPARATOR = "\n"
    }
}
