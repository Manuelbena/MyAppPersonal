package com.syncro.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.syncro.domain.repository.DailyQuoteRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.quoteDataStore: DataStore<Preferences> by preferencesDataStore(name = "daily_quote")

/** El día en que se ocultó la frase, en DataStore (un solo valor; basta con el último día). */
@Singleton
class DailyQuoteRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DailyQuoteRepository {

    private val HIDDEN_ON = stringPreferencesKey("hidden_on")

    override val hiddenOn: Flow<LocalDate?> = context.quoteDataStore.data.map { prefs ->
        prefs[HIDDEN_ON]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    override suspend fun hide(date: LocalDate) {
        context.quoteDataStore.edit { it[HIDDEN_ON] = date.toString() }
    }
}
