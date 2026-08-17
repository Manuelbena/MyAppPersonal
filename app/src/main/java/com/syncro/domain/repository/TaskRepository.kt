package com.syncro.domain.repository

import com.syncro.domain.model.SyncroItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface TaskRepository {
    fun getTasksByDate(date: LocalDate): Flow<List<SyncroItem.Task>>
    suspend fun insertTask(task: SyncroItem.Task, date: LocalDate)
    suspend fun toggleTaskCompletion(taskId: String)
}
