package com.syncro.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.syncro.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE date = :dateEpoch ORDER BY time ASC")
    fun getTasksByDate(dateEpoch: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE date >= :startEpoch AND date <= :endEpoch ORDER BY date ASC, time ASC")
    fun getTasksInRange(startEpoch: Long, endEpoch: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE remoteId = :remoteId")
    suspend fun getTaskByRemoteId(remoteId: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Query("UPDATE tasks SET isCompleted = NOT isCompleted, pendingChanges = pendingChanges + 1 WHERE id = :id")
    suspend fun toggleTaskCompletion(id: String)

    // region Sincronización

    /** Tareas con cambios locales sin subir o que nunca llegaron a Google. */
    @Query("SELECT id FROM tasks WHERE pendingChanges > 0 OR remoteId IS NULL")
    suspend fun getPendingTaskIds(): List<String>

    /** remoteIds de tareas sin cambios pendientes: las únicas que puede borrar la sync. */
    @Query("SELECT remoteId FROM tasks WHERE remoteId IS NOT NULL AND pendingChanges = 0")
    suspend fun getSyncedRemoteIds(): List<String>

    /**
     * Guarda el remoteId y limpia los pendientes solo si no hubo cambios nuevos desde
     * [expectedPending]; si los hubo, siguen pendientes para la próxima subida.
     */
    @Query(
        "UPDATE tasks SET remoteId = :remoteId, " +
            "pendingChanges = CASE WHEN pendingChanges = :expectedPending THEN 0 ELSE pendingChanges END " +
            "WHERE id = :id"
    )
    suspend fun markSynced(id: String, remoteId: String, expectedPending: Int)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)

    @Query("DELETE FROM tasks WHERE remoteId IN (:remoteIds)")
    suspend fun deleteByRemoteIds(remoteIds: List<String>)

    // endregion
}
