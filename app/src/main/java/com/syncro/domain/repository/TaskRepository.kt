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
    /** La quita de la app al momento; el borrado en Google queda pendiente de subir. */
    suspend fun deleteTask(taskId: String)

    /** Deshace [deleteTask] si el borrado aún no se ha subido a Google; false si ya no se puede. */
    suspend fun restoreTask(taskId: String): Boolean
    /** Pasa la tarea a otro día (misma hora); la nueva fecha queda pendiente de subir a Google. */
    suspend fun moveTask(taskId: String, date: LocalDate)
    /** Tareas sin hacer hasta ese día incluido, también las atrasadas. */
    fun getUnfinishedTasksUntil(date: LocalDate): Flow<List<SyncroItem.Task>>
    /** Ids de las repeticiones de la serie desde [from] incluido (sin las borradas). */
    suspend fun getTaskIdsInSeries(seriesId: String, from: LocalDate): List<String>
    /** Las que tienen aviso y aún pueden sonar: sin hacer, sin borrar, desde [from]. */
    fun observeTasksWithReminder(from: LocalDate): Flow<List<SyncroItem.Task>>
}
