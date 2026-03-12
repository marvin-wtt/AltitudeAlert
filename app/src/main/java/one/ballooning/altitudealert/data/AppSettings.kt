package one.ballooning.altitudealert.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private object Keys {
    val ALERT_CONFIG = stringPreferencesKey("alert_config")
}

class AppSettings(context: Context) {

    private val store = context.dataStore

    val configFlow: Flow<AlertConfig> = store.data.map { prefs ->
        prefs[Keys.ALERT_CONFIG]
            ?.let { runCatching { Json.decodeFromString<AlertConfig>(it) }.getOrNull() }
            ?: defaultConfig()
    }

    suspend fun saveConfig(config: AlertConfig) {
        store.edit { prefs ->
            prefs[Keys.ALERT_CONFIG] = Json.encodeToString(config)
        }
    }

    suspend fun updateQnh(qnhHpa: Float) {
        store.edit { prefs ->
            val current = prefs[Keys.ALERT_CONFIG]
                ?.let { runCatching { Json.decodeFromString<AlertConfig>(it) }.getOrNull() }
                ?: defaultConfig()
            prefs[Keys.ALERT_CONFIG] = Json.encodeToString(current.copy(qnhHpa = qnhHpa))
        }
    }

    companion object {
        fun defaultConfig() = AlertConfig()
    }
}