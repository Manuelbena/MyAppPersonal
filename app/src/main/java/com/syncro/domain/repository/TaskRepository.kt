package com.syncro.domain.repository

import com.syncro.domain.model.SyncroItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface TaskRepository {
    fun getTasksByDate(date: LocalDate): Flow<List<SyncroItem.Task>>
    fun getTasksInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<SyncroItem.Task>>
    suspend fun insertTask(task: SyncroItem.Task, date: LocalDate): String
    suspend fun getTaskById(taskId: String): SyncroItem.Task?
    suspend fun toggleTaskCompletion(taskId: String)
}
