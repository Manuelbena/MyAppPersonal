package com.syncro.domain.repository

import com.syncro.domain.model.SyncroItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface TaskRepository {
    fun getTasksByDate(date: LocalDate): Flow<List<SyncroItem.Task>>
    fun getTasksInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<SyncroItem.Task>>
    /** Crea la tarea con el id que trae (lo asigna el dominio). */
    suspend fun insertTask(task: SyncroItem.Task)
    suspend fun getTaskById(taskId: String): SyncroItem.Task?
    suspend fun toggleTaskCompletion(taskId: String)
    /** Pasa la tarea a otro día (misma hora); la nueva fecha queda pendiente de subir a Google. */
    suspend fun moveTask(taskId: String, date: LocalDate)
    /** Tareas sin hacer hasta ese día incluido, también las atrasadas. */
    fun getUnfinishedTasksUntil(date: LocalDate): Flow<List<SyncroItem.Task>>
}
