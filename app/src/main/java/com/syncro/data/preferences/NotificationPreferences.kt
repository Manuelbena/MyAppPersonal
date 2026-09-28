package com.syncro.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.syncro.domain.model.DigestAnswer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.notificationDataStore: DataStore<Preferences> by preferencesDataStore(name = "notification_prefs")

/**
 * Estado del chat del asistente: qué contestó el usuario a los avisos diarios y qué mensajes
 * ya ha visto (para el número del icono de Asistente).
 */
@Singleton
class NotificationPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val DIGEST_ANSWER = stringPreferencesKey("digest_answer")
    private val READ_MESSAGE_IDS = stringSetPreferencesKey("assistant_read_message_ids")
    private val CLEARED_MESSAGE_IDS = stringSetPreferencesKey("assistant_cleared_message_ids")

    val digestAnswer: Flow<DigestAnswer?> = context.notificationDataStore.data
        .map { prefs -> prefs[DIGEST_ANSWER]?.let { name -> DigestAnswer.entries.firstOrNull { it.name == name } } }

    val readMessageIds: Flow<Set<String>> = context.notificationDataStore.data
        .map { it[READ_MESSAGE_IDS] ?: emptySet() }

    suspend fun saveDigestAnswer(answer: DigestAnswer) {
        context.notificationDataStore.edit { it[DIGEST_ANSWER] = answer.name }
    }

    val clearedMessageIds: Flow<Set<String>> = context.notificationDataStore.data
        .map { it[CLEARED_MESSAGE_IDS] ?: emptySet() }

    suspend fun markRead(ids: Set<String>) {
        context.notificationDataStore.edit { it[READ_MESSAGE_IDS] = (it[READ_MESSAGE_IDS] ?: emptySet()) + ids }
    }

    /** Vaciar el chat: esos mensajes dejan de mostrarse (los nuevos sí saldrán). */
    suspend fun clearMessages(ids: Set<String>) {
        context.notificationDataStore.edit { it[CLEARED_MESSAGE_IDS] = (it[CLEARED_MESSAGE_IDS] ?: emptySet()) + ids }
    }
}
