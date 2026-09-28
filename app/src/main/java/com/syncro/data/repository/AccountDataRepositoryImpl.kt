package com.syncro.data.repository

import com.syncro.data.local.SyncroDatabase
import com.syncro.data.preferences.AssistantPreferences
import com.syncro.domain.model.DataLossSummary
import com.syncro.domain.repository.AccountDataRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Borra lo que pertenece a la cuenta: la base de datos (tareas, eventos, notas, sesión), el chat
 * del asistente y las prioridades. Los ajustes y el tema se quedan: son del móvil, no de la cuenta.
 */
@Singleton
class AccountDataRepositoryImpl @Inject constructor(
    private val database: SyncroDatabase,
    private val assistantPreferences: AssistantPreferences,
    private val dailyFocus: DailyFocusRepositoryImpl
) : AccountDataRepository {

    override suspend fun dataLossSummary(): DataLossSummary = DataLossSummary(
        unsyncedChanges = database.taskDao.getPendingTaskIds().size + database.eventDao.getPendingEventIds().size,
        notes = database.noteDao.getAllNotes().first().size
    )

    override suspend fun clearAccountData() {
        assistantPreferences.clearAll()
        dailyFocus.clearAll()
        // La base de datos la última: al borrarla desaparece la sesión y la app vuelve al login
        withContext(Dispatchers.IO) { database.clearAllTables() }
    }
}
