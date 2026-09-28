package com.syncro.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.syncro.domain.model.DailyFocus
import com.syncro.domain.model.FocusedTask
import com.syncro.domain.repository.DailyFocusRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.focusDataStore: DataStore<Preferences> by preferencesDataStore(name = "daily_focus")

/**
 * Prioridades en DataStore, una clave por día ("focus_2026-09-28"). Son pocas y pequeñas, y no se
 * sincronizan, así que no hace falta una tabla (ni una migración de Room).
 */
@Singleton
class DailyFocusRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DailyFocusRepository {

    override fun getFocus(date: LocalDate): Flow<DailyFocus?> = context.focusDataStore.data
        .map { prefs -> prefs[key(date)]?.let { decodeFocus(date, it) } }

    override fun getFocusSince(date: LocalDate): Flow<List<DailyFocus>> = context.focusDataStore.data.map { prefs ->
        prefs.asMap().mapNotNull { (key, value) ->
            val day = key.name.removePrefix(PREFIX).takeIf { key.name.startsWith(PREFIX) }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: return@mapNotNull null
            if (day.isBefore(date)) null else decodeFocus(day, value as? String ?: return@mapNotNull null)
        }.sortedBy { it.date }
    }

    override suspend fun saveFocus(focus: DailyFocus) {
        context.focusDataStore.edit { it[key(focus.date)] = encodeFocus(focus) }
    }

    /** Al cerrar sesión: las prioridades son de la cuenta. */
    suspend fun clearAll() {
        context.focusDataStore.edit { it.clear() }
    }

    private fun key(date: LocalDate) = stringPreferencesKey("$PREFIX$date")

    private companion object {
        const val PREFIX = "focus_"
    }
}

// Separadores de control (no se escriben en un título): tarea y campo
private const val TASK_SEPARATOR = '\u001E'
private const val FIELD_SEPARATOR = '\u001F'

/** "id␟título␞id␟título"; vacío = "Hoy no". */
internal fun encodeFocus(focus: DailyFocus): String =
    focus.tasks.joinToString(TASK_SEPARATOR.toString()) { "${it.id}$FIELD_SEPARATOR${it.title}" }

internal fun decodeFocus(date: LocalDate, value: String): DailyFocus = DailyFocus(
    date,
    value.split(TASK_SEPARATOR).filter { it.isNotEmpty() }.mapNotNull { entry ->
        val id = entry.substringBefore(FIELD_SEPARATOR)
        if (id.isEmpty()) null else FocusedTask(id, entry.substringAfter(FIELD_SEPARATOR, ""))
    }
)
