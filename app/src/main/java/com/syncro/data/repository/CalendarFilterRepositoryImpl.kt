package com.syncro.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.syncro.domain.repository.CalendarFilterRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.calendarFilterDataStore: DataStore<Preferences> by preferencesDataStore(name = "calendar_filter")

/** Las categorías ocultas del Calendario, en DataStore (sobreviven a cerrar la app). */
@Singleton
class CalendarFilterRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : CalendarFilterRepository {

    private val HIDDEN = stringSetPreferencesKey("hidden_categories")

    override val hiddenCategories: Flow<Set<String>> =
        context.calendarFilterDataStore.data.map { it[HIDDEN].orEmpty() }

    override suspend fun setHiddenCategories(categories: Set<String>) {
        context.calendarFilterDataStore.edit { it[HIDDEN] = categories }
    }
}
