package one.ballooning.altitudealert.data.repository

import kotlinx.coroutines.flow.Flow
import one.ballooning.altitudealert.data.model.AlertConfig

interface ConfigRepository {
    val configFlow: Flow<AlertConfig>
    suspend fun save(config: AlertConfig)
}