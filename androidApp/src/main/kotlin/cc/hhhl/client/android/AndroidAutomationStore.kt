package cc.hhhl.client.android

import android.content.Context
import cc.hhhl.client.automation.AutomationSnapshot
import cc.hhhl.client.automation.AutomationStore
import cc.hhhl.client.automation.AutomationStoreCodec

class AndroidAutomationStore(context: Context) : AutomationStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun read(accountId: String): AutomationSnapshot {
        return AutomationStoreCodec.decode(preferences.getString(keyFor(accountId), null))
    }

    override fun write(accountId: String, snapshot: AutomationSnapshot) {
        preferences.edit()
            .putString(keyFor(accountId), AutomationStoreCodec.encode(snapshot))
            .apply()
    }

    override fun clearAccount(accountId: String) {
        preferences.edit()
            .remove(keyFor(accountId))
            .apply()
    }

    private fun keyFor(accountId: String): String {
        return "$KEY_PREFIX${accountId.trim().encodeKeyPart()}"
    }

    private fun String.encodeKeyPart(): String {
        return replace("%", "%25").replace(":", "%3A").replace("\n", "%0A")
    }

    private companion object {
        const val PREFERENCES_NAME = "hhhl_automation"
        const val KEY_PREFIX = "snapshot_"
    }
}
