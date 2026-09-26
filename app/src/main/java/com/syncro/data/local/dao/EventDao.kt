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
    @Query("SELECT * FROM events WHERE date = :dateEpoch")
    fun getEventsByDate(dateEpoch: Long): Flow<List<EventWithSubtasks>>

    @Transaction
    @Query("SELECT * FROM events WHERE date >= :startEpoch AND date <= :endEpoch")
    fun getEventsInRange(startEpoch: Long, endEpoch: Long): Flow<List<EventWithSubtasks>>

    @Transaction
    @Query("SELECT * FROM events WHERE remoteId IS NULL AND date = :dateEpoch")
    suspend fun getUnsyncedEventsByDate(dateEpoch: Long): List<EventWithSubtasks>

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

    @Query("SELECT remoteId FROM events WHERE date >= :startEpoch AND date <= :endEpoch AND remoteId IS NOT NULL")
    suspend fun getRemoteIdsInRange(startEpoch: Long, endEpoch: Long): List<String>

    @Query("DELETE FROM events WHERE remoteId IN (:remoteIds)")
    suspend fun deleteByRemoteIds(remoteIds: List<String>)

    @Query("UPDATE events SET remoteId = :remoteId WHERE id = :id")
    suspend fun updateRemoteId(id: String, remoteId: String)

    @Query("UPDATE events SET isCompleted = NOT isCompleted WHERE id = :eventId")
    suspend fun toggleEventCompletion(eventId: String)

    @Query("UPDATE subtasks SET isCompleted = NOT isCompleted WHERE eventId = :eventId AND title = :subtaskTitle")
    suspend fun toggleSubtaskCompletion(eventId: String, subtaskTitle: String)
}
