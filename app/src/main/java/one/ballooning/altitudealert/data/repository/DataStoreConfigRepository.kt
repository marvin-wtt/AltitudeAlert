package one.ballooning.altitudealert.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import one.ballooning.altitudealert.data.model.AlertConfig

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreConfigRepository(private val context: Context) : ConfigRepository {

    override val configFlow: Flow<AlertConfig> = context.dataStore.data.map { prefs ->
        prefs[KEY_CONFIG]
            ?.let { runCatching { Json.decodeFromString<AlertConfig>(it) }.getOrNull() }
            ?: AlertConfig()
    }

    override suspend fun save(config: AlertConfig) {
        context.dataStore.edit { it[KEY_CONFIG] = Json.encodeToString(config) }
    }

    override suspend fun updateQnh(qnhHpa: Float) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_CONFIG]
                ?.let { runCatching { Json.decodeFromString<AlertConfig>(it) }.getOrNull() }
                ?: AlertConfig()
            prefs[KEY_CONFIG] = Json.encodeToString(current.copy(qnhHpa = qnhHpa))
        }
    }

    companion object {
        private val KEY_CONFIG = stringPreferencesKey("alert_config")
    }
}