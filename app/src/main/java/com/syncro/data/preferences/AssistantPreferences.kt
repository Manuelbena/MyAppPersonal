package com.syncro.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.syncro.domain.model.DigestAnswer
import com.syncro.domain.model.HabitAnswer
import com.syncro.domain.model.LeftoverChoice
import com.syncro.domain.model.LeftoverOutcome
import com.syncro.domain.model.MoveTarget
import com.syncro.domain.model.PaydayAnswer
import com.syncro.domain.model.StuckAnswer
import com.syncro.domain.model.StuckChoice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

// Mismo nombre de archivo que cuando solo guardaba lo de las notificaciones, para no perder datos
private val Context.assistantDataStore: DataStore<Preferences> by preferencesDataStore(name = "notification_prefs")

/**
 * Estado del chat del asistente: qué contestó el usuario a los avisos diarios y el día de nómina, qué decidió en cada
 * repaso de tareas pendientes y qué mensajes ya ha visto o ha borrado.
 */
@Singleton
class AssistantPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val DIGEST_ANSWER = stringPreferencesKey("digest_answer")
    private val READ_MESSAGE_IDS = stringSetPreferencesKey("assistant_read_message_ids")
    private val CLEARED_MESSAGE_IDS = stringSetPreferencesKey("assistant_cleared_message_ids")
    private val DISMISSED_UPDATE_VERSION = intPreferencesKey("dismissed_update_version")

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

    /** Lo contestado cada día de nómina; una clave por día ("payday_2026-10-28"). */
    val paydayAnswers: Flow<Map<LocalDate, PaydayAnswer>> = context.assistantDataStore.data.map { prefs ->
        prefs.asMap().mapNotNull { (key, value) ->
            if (!key.name.startsWith(PAYDAY_PREFIX)) return@mapNotNull null
            runCatching {
                LocalDate.parse(key.name.removePrefix(PAYDAY_PREFIX)) to PaydayAnswer.valueOf(value as String)
            }.getOrNull()
        }.toMap()
    }

    suspend fun savePaydayAnswer(date: LocalDate, answer: PaydayAnswer) {
        context.assistantDataStore.edit { it[stringPreferencesKey("$PAYDAY_PREFIX$date")] = answer.name }
    }

    /** Lo contestado a cada hábito propuesto (clave `habit_<key>`, valor `SI|NO;fecha;día;título`). */
    val habitAnswers: Flow<List<HabitAnswer>> = context.assistantDataStore.data.map { prefs ->
        prefs.asMap().mapNotNull { (key, value) ->
            if (!key.name.startsWith(HABIT_PREFIX)) return@mapNotNull null
            runCatching {
                val (accepted, date, weekday, title) = (value as String).split(";", limit = 4)
                HabitAnswer(key.name.removePrefix(HABIT_PREFIX), title, DayOfWeek.valueOf(weekday), LocalDate.parse(date), accepted == "SI")
            }.getOrNull()
        }
    }

    suspend fun saveHabitAnswer(answer: HabitAnswer) {
        val value = listOf(if (answer.accepted) "SI" else "NO", answer.date, answer.weekday.name, answer.title).joinToString(";")
        context.assistantDataStore.edit { it[stringPreferencesKey("$HABIT_PREFIX${answer.key}")] = value }
    }

    /** Los días de repaso por los que pasó cada tarea sin hacerse (clave `postponed_<id>`). */
    val postponedReviews: Flow<Map<String, Set<LocalDate>>> = context.assistantDataStore.data.map { prefs ->
        prefs.asMap().mapNotNull { (key, value) ->
            if (!key.name.startsWith(POSTPONED_PREFIX)) return@mapNotNull null
            val dates = (value as? Set<*>)?.mapNotNull { runCatching { LocalDate.parse(it as String) }.getOrNull() }?.toSet()
            dates?.let { key.name.removePrefix(POSTPONED_PREFIX) to it }
        }.toMap()
    }

    /**
     * Apunta que [taskIds] pasaron por el repaso de [reviewDate] (el mismo repaso cuenta una vez).
     * Olvida las tareas sin repasos en los últimos [POSTPONED_KEEP_DAYS] días: ya se hicieron o borraron.
     */
    suspend fun recordPostponed(taskIds: Collection<String>, reviewDate: LocalDate) {
        val cutoff = reviewDate.minusDays(POSTPONED_KEEP_DAYS)
        context.assistantDataStore.edit { prefs ->
            prefs.asMap().toMap().forEach { (key, value) ->
                if (!key.name.startsWith(POSTPONED_PREFIX)) return@forEach
                val latest = (value as? Set<*>)?.mapNotNull { runCatching { LocalDate.parse(it as String) }.getOrNull() }?.maxOrNull()
                if (latest == null || latest.isBefore(cutoff)) prefs.remove(key)
            }
            taskIds.forEach { id ->
                val key = stringSetPreferencesKey("$POSTPONED_PREFIX$id")
                prefs[key] = (prefs[key] ?: emptySet()) + reviewDate.toString()
            }
        }
    }

    /** Lo contestado sobre cada tarea atascada (clave `stuck_<id>`, valor `ELECCIÓN;repasos;último;título`). */
    val stuckAnswers: Flow<List<StuckAnswer>> = context.assistantDataStore.data.map { prefs ->
        prefs.asMap().mapNotNull { (key, value) ->
            if (!key.name.startsWith(STUCK_PREFIX)) return@mapNotNull null
            runCatching {
                val (choice, reviews, lastReview, title) = (value as String).split(";", limit = 4)
                StuckAnswer(key.name.removePrefix(STUCK_PREFIX), title, reviews.toInt(), LocalDate.parse(lastReview), StuckChoice.valueOf(choice))
            }.getOrNull()
        }
    }

    suspend fun saveStuckAnswer(answer: StuckAnswer) {
        val value = listOf(answer.choice.name, answer.reviews, answer.lastReview, answer.title).joinToString(";")
        context.assistantDataStore.edit { it[stringPreferencesKey("$STUCK_PREFIX${answer.taskId}")] = value }
    }

    /** La versión nueva a la que se contestó "Más tarde" (una más nueva se vuelve a anunciar). */
    val dismissedUpdateVersion: Flow<Int?> = context.assistantDataStore.data.map { it[DISMISSED_UPDATE_VERSION] }

    suspend fun dismissUpdate(versionCode: Int) {
        context.assistantDataStore.edit { it[DISMISSED_UPDATE_VERSION] = versionCode }
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

    /** Cuándo salió cada mensaje por primera vez ("id|2026-10-19T09:00"): da el orden y el día del chat. */
    val messageArrivals: Flow<Map<String, LocalDateTime>> = context.assistantDataStore.data.map { prefs ->
        prefs[MESSAGE_ARRIVALS].orEmpty().mapNotNull { entry ->
            val id = entry.substringBeforeLast('|')
            runCatching { id to LocalDateTime.parse(entry.substringAfterLast('|')) }.getOrNull()
        }.toMap()
    }

    /** Guarda la llegada de mensajes nuevos (los ya guardados no cambian) y olvida los muy antiguos. */
    suspend fun saveMessageArrivals(arrivals: Map<String, LocalDateTime>) {
        context.assistantDataStore.edit { prefs ->
            val stored = prefs[MESSAGE_ARRIVALS].orEmpty()
            val known = stored.map { it.substringBeforeLast('|') }.toSet()
            val keepFrom = arrivals.values.maxOrNull()?.minusDays(ARRIVALS_KEEP_DAYS)
            val kept = stored.filter { entry ->
                val at = runCatching { LocalDateTime.parse(entry.substringAfterLast('|')) }.getOrNull()
                at != null && (keepFrom == null || !at.isBefore(keepFrom))
            }
            prefs[MESSAGE_ARRIVALS] = kept.toSet() + arrivals.filterKeys { it !in known }.map { (id, at) -> "$id|$at" }
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
        const val PAYDAY_PREFIX = "payday_"
        const val HABIT_PREFIX = "habit_"
        const val POSTPONED_PREFIX = "postponed_"
        const val STUCK_PREFIX = "stuck_"
        const val POSTPONED_KEEP_DAYS = 60L
        const val ARRIVALS_KEEP_DAYS = 60L
        val MESSAGE_ARRIVALS = stringSetPreferencesKey("assistant_message_arrivals")
    }
}
