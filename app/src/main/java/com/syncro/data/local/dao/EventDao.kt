package com.syncro.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.syncro.data.local.entity.EventEntity
import com.syncro.data.local.entity.EventWithSubtasks
import com.syncro.data.local.entity.SubtaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Transaction
    // Todos los que tocan el día (incluidos los que empezaron antes); la regla exacta está en el dominio
    @Query("SELECT * FROM events WHERE date <= :dateEpoch AND endDate >= :dateEpoch AND isDeleted = 0")
    fun getEventsByDate(dateEpoch: Long): Flow<List<EventWithSubtasks>>

    @Transaction
    @Query("SELECT * FROM events WHERE date <= :endEpoch AND endDate >= :startEpoch AND isDeleted = 0")
    fun getEventsInRange(startEpoch: Long, endEpoch: Long): Flow<List<EventWithSubtasks>>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getEventById(id: String): EventEntity?

    @Query("SELECT * FROM events WHERE remoteId = :remoteId")
    suspend fun getEventByRemoteId(remoteId: String): EventEntity?

    @Query("SELECT * FROM subtasks WHERE eventId = :eventId")
    suspend fun getSubtasksForEvent(eventId: String): List<SubtaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtasks(subtasks: List<SubtaskEntity>)

    @Query("DELETE FROM subtasks WHERE eventId = :eventId")
    suspend fun deleteSubtasksForEvent(eventId: String)

    /** Inserta/reemplaza el evento y sustituye por completo su lista de subtareas. */
    @Transaction
    suspend fun insertEventWithSubtasks(event: EventEntity, subtasks: List<SubtaskEntity>) {
        insertEvent(event)
        deleteSubtasksForEvent(event.id)
        insertSubtasks(subtasks.map { it.copy(eventId = event.id) })
    }

    @Query("UPDATE events SET isCompleted = NOT isCompleted, pendingChanges = pendingChanges + 1 WHERE id = :eventId")
    suspend fun toggleEventCompletion(eventId: String)

    @Query("UPDATE subtasks SET isCompleted = NOT isCompleted WHERE eventId = :eventId AND title = :subtaskTitle")
    suspend fun toggleSubtaskRow(eventId: String, subtaskTitle: String)

    @Query("UPDATE events SET pendingChanges = pendingChanges + 1 WHERE id = :eventId")
    suspend fun markEventChanged(eventId: String)

    /** Borrado en la app: deja de verse y queda pendiente de borrar en Google (ver pushEvent). */
    @Query("UPDATE events SET isDeleted = 1, pendingChanges = pendingChanges + 1 WHERE id = :eventId")
    suspend fun markEventDeleted(eventId: String)

    /** Ver [TaskDao.restoreTask]. */
    @Query("UPDATE events SET isDeleted = 0 WHERE id = :eventId AND isDeleted = 1")
    suspend fun restoreEvent(eventId: String): Int

    /** Las subtareas se sincronizan dentro de la descripción del evento: cambiar una marca el evento. */
    @Transaction
    suspend fun toggleSubtaskCompletion(eventId: String, subtaskTitle: String) {
        toggleSubtaskRow(eventId, subtaskTitle)
        markEventChanged(eventId)
    }

    /** Los que tienen aviso y aún pueden sonar: sin completar, sin borrar y desde [fromEpoch]. */
    @Transaction
    @Query("SELECT * FROM events WHERE reminderMinutes IS NOT NULL AND isCompleted = 0 AND isDeleted = 0 AND date >= :fromEpoch")
    fun observeWithReminder(fromEpoch: Long): Flow<List<EventWithSubtasks>>

    @Query("SELECT id FROM events WHERE seriesId = :seriesId AND date >= :fromEpoch AND isDeleted = 0")
    suspend fun getIdsInSeries(seriesId: String, fromEpoch: Long): List<String>

    // region Sincronización

    /** Eventos con cambios locales sin subir o que nunca llegaron a Google. */
    @Query("SELECT id FROM events WHERE pendingChanges > 0 OR remoteId IS NULL")
    suspend fun getPendingEventIds(): List<String>

    /** Ver [TaskDao.observePendingCount]. */
    @Query("SELECT COUNT(*) FROM events WHERE pendingChanges > 0 OR remoteId IS NULL")
    fun observePendingCount(): Flow<Int>

    /** remoteIds sin cambios pendientes que empiezan en el rango: los únicos que puede borrar la sync. */
    @Query(
        "SELECT remoteId FROM events WHERE date >= :startEpoch AND date <= :endEpoch " +
            "AND remoteId IS NOT NULL AND pendingChanges = 0"
    )
    suspend fun getSyncedRemoteIdsInRange(startEpoch: Long, endEpoch: Long): List<String>

    /** Ver [TaskDao.markSynced]. */
    @Query(
        "UPDATE events SET remoteId = :remoteId, " +
            "pendingChanges = CASE WHEN pendingChanges = :expectedPending THEN 0 ELSE pendingChanges END " +
            "WHERE id = :id"
    )
    suspend fun markSynced(id: String, remoteId: String, expectedPending: Int)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteEventById(id: String)

    @Query("DELETE FROM events WHERE remoteId IN (:remoteIds)")
    suspend fun deleteByRemoteIds(remoteIds: List<String>)

    // endregion
}
