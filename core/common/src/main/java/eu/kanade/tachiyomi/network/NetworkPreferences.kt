package eu.kanade.tachiyomi.network

import eu.kanade.tachiyomi.util.system.WebViewUtil
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class NetworkPreferences(
    private val preferenceStore: PreferenceStore,
    private val verboseLogging: Boolean = false,
    private val fallbackUserAgent: String = WebViewUtil.getDefaultUserAgent(),
) {

    fun verboseLogging(): Preference<Boolean> {
        return preferenceStore.getBoolean("verbose_logging", verboseLogging)
    }

    fun dohProvider(): Preference<Int> {
        return preferenceStore.getInt("doh_provider", -1)
    }

    fun defaultUserAgent(): Preference<String> {
        return preferenceStore.getString("default_user_agent", fallbackUserAgent)
    }
}
