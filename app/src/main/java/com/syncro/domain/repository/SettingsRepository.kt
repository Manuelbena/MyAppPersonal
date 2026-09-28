package com.syncro.domain.repository

import com.syncro.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

/** Ajustes del usuario en este móvil (no se sincronizan ni se borran al cerrar sesión). */
interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun save(settings: AppSettings)
}
