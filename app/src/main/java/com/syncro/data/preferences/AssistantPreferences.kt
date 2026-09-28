package com.syncro.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.syncro.domain.model.DigestAnswer
import com.syncro.domain.model.LeftoverChoice
import com.syncro.domain.model.LeftoverOutcome
import com.syncro.domain.model.MoveTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

// Mismo nombre de archivo que cuando solo guardaba lo de las notificaciones, para no perder datos
private val Context.assistantDataStore: DataStore<Preferences> by preferencesDataStore(name = "notification_prefs")

/**
 * Estado del chat del asistente: qué contestó el usuario a los avisos diarios, qué decidió en cada
 * repaso de tareas pendientes y qué mensajes ya ha visto o ha borrado.
 */
@Singleton
class AssistantPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val DIGEST_ANSWER = stringPreferencesKey("digest_answer")
    private val READ_MESSAGE_IDS = stringSetPreferencesKey("assistant_read_message_ids")
    private val CLEARED_MESSAGE_IDS = stringSetPreferencesKey("assistant_cleared_message_ids")

    val digestAnswer: Flow<DigestAnswer?> = context.assistantDataStore.data
        .map { prefs -> prefs[DIGEST_ANSWER]?.let { name -> DigestAnswer.entries.firstOrNull { it.name == name } } }

    val readMessageIds: Flow<Set<String>> = context.assistantDataStore.data
        .map { it[READ_MESSAGE_IDS] ?: emptySet() }

    val clearedMessageIds: Flow<Set<String>> = context.assistantDataStore.data
        .map { it[CLEARED_MESSAGE_IDS] ?: emptySet() }

    /** Una clave por repaso ("leftovers_2026-09-28"); se ignoran las que no se puedan leer. */
    val leftoverOutcomes: Flow<List<LeftoverOutcome>> = context.assistantDataStore.data.map { prefs ->
        prefs.asMap().mapNotNull { (key, value) ->
            if (!key.name.startsWith(OUTCOME_PREFIX)) return@mapNotNull null
            decodeOutcome(key.name.removePrefix(OUTCOME_PREFIX), value as? String ?: return@mapNotNull null)
        }
    }

    suspend fun saveDigestAnswer(answer: DigestAnswer) {
        context.assistantDataStore.edit { it[DIGEST_ANSWER] = answer.name }
    }

    suspend fun saveLeftoverOutcome(outcome: LeftoverOutcome) {
        context.assistantDataStore.edit { it[outcomeKey(outcome.reviewDate)] = encodeOutcome(outcome) }
    }

    /** Suma una tarea resuelta al repaso "una a una" (movida o hecha). */
    suspend fun countLeftoverResolved(reviewDate: LocalDate, moved: Boolean) {
        context.assistantDataStore.edit { prefs ->
            val key = outcomeKey(reviewDate)
            val outcome = prefs[key]?.let { decodeOutcome(reviewDate.toString(), it) } ?: return@edit
            prefs[key] = encodeOutcome(
                if (moved) outcome.copy(moved = outcome.moved + 1) else outcome.copy(done = outcome.done + 1)
            )
        }
    }

    suspend fun markRead(ids: Set<String>) {
        context.assistantDataStore.edit { it[READ_MESSAGE_IDS] = (it[READ_MESSAGE_IDS] ?: emptySet()) + ids }
    }

    /** Al cerrar sesión: el chat de esta cuenta no debe verlo la siguiente. */
    suspend fun clearAll() {
        context.assistantDataStore.edit { it.clear() }
    }

    /** Vaciar el chat: esos mensajes dejan de mostrarse (los nuevos sí saldrán). */
    suspend fun clearMessages(ids: Set<String>) {
        context.assistantDataStore.edit { it[CLEARED_MESSAGE_IDS] = (it[CLEARED_MESSAGE_IDS] ?: emptySet()) + ids }
    }

    private fun outcomeKey(reviewDate: LocalDate) = stringPreferencesKey("$OUTCOME_PREFIX$reviewDate")

    // "MOVE_ALL;TOMORROW;3;0;0": elección, día propuesto, total, movidas, hechas
    private fun encodeOutcome(o: LeftoverOutcome) = listOf(o.choice.name, o.target.name, o.total, o.moved, o.done).joinToString(";")

    private fun decodeOutcome(date: String, value: String): LeftoverOutcome? = runCatching {
        val (choice, target, total, moved, done) = value.split(";")
        LeftoverOutcome(
            reviewDate = LocalDate.parse(date),
            choice = LeftoverChoice.valueOf(choice),
            target = MoveTarget.valueOf(target),
            total = total.toInt(),
            moved = moved.toInt(),
            done = done.toInt()
        )
    }.getOrNull()

    private companion object {
        const val OUTCOME_PREFIX = "leftovers_"
    }
}
