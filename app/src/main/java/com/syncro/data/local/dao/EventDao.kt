package com.syncro.data.local.dao

import androidx.room.*
import com.syncro.data.local.entity.EventEntity
import com.syncro.data.local.entity.EventWithSubtasks
import com.syncro.data.local.entity.SubtaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Transaction
    @Query("SELECT * FROM events WHERE date = :dateEpoch")
    fun getEventsByDate(dateEpoch: Long): Flow<List<EventWithSubtasks>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtasks(subtasks: List<SubtaskEntity>)

    @Transaction
    @Query("SELECT * FROM subtasks WHERE eventId = :eventId")
    suspend fun getSubtasksForEvent(eventId: String): List<SubtaskEntity>

    @Transaction
    suspend fun insertEventWithSubtasks(event: EventEntity, subtasks: List<SubtaskEntity>) {
        insertEvent(event)
        val subtasksWithId = subtasks.map { it.copy(eventId = event.id) }
        insertSubtasks(subtasksWithId)
    }

    @Query("DELETE FROM events WHERE remoteId = :remoteId")
    suspend fun deleteEventByRemoteId(remoteId: String)

    @Query("UPDATE subtasks SET isCompleted = NOT isCompleted WHERE eventId = :eventId AND title = :subtaskTitle")
    suspend fun toggleSubtaskCompletion(eventId: String, subtaskTitle: String)

    @Query("UPDATE events SET isCompleted = NOT isCompleted WHERE id = :eventId")
    suspend fun toggleEventCompletion(eventId: String)

    @Transaction
    @Query("SELECT * FROM events WHERE date >= :startEpoch AND date <= :endEpoch")
    fun getEventsInRange(startEpoch: Long, endEpoch: Long): Flow<List<EventWithSubtasks>>

    @Transaction
    @Query("SELECT * FROM events WHERE remoteId IS NULL AND date = :dateEpoch")
    suspend fun getUnsyncedEventsByDate(dateEpoch: Long): List<EventWithSubtasks>
}
