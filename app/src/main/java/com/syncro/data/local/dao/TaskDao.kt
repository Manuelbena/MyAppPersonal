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

    @Query("SELECT * FROM tasks WHERE remoteId IS NULL AND date = :dateEpoch")
    suspend fun getUnsyncedTasksByDate(dateEpoch: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE remoteId = :remoteId")
    suspend fun getTaskByRemoteId(remoteId: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Query("SELECT remoteId FROM tasks WHERE remoteId IS NOT NULL")
    suspend fun getAllRemoteIds(): List<String>

    @Query("DELETE FROM tasks WHERE remoteId IN (:remoteIds)")
    suspend fun deleteByRemoteIds(remoteIds: List<String>)

    @Query("UPDATE tasks SET remoteId = :remoteId WHERE id = :id")
    suspend fun updateRemoteId(id: String, remoteId: String)

    @Query("UPDATE tasks SET isCompleted = NOT isCompleted WHERE id = :id")
    suspend fun toggleTaskCompletion(id: String)
}
