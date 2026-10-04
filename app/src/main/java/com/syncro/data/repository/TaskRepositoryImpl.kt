package com.syncro.data.repository

import com.syncro.data.local.toLocalTimeOrMidnight
import com.syncro.data.local.toStoredTime
import com.syncro.domain.model.ArgbColor
import com.syncro.data.local.dao.RepeatSeriesDao
import com.syncro.data.local.dao.TaskDao
import com.syncro.data.local.entity.TaskEntity
import com.syncro.domain.model.Recurrence
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val dao: TaskDao,
    private val seriesDao: RepeatSeriesDao
) : TaskRepository {

    // Cómo se repite cada serie, para enseñarlo en cada repetición
    private val repeats: Flow<Map<String, Recurrence?>> =
        seriesDao.observeAll().map { all -> all.associate { it.id to it.toRecurrence() } }

    private fun Flow<List<TaskEntity>>.toDomain(): Flow<List<SyncroItem.Task>> =
        combine(this, repeats) { entities, repeats -> entities.map { it.toDomain(repeats) } }

    override fun getTasksByDate(date: LocalDate): Flow<List<SyncroItem.Task>> =
        dao.getTasksByDate(date.toEpochDay()).toDomain()

    override fun getTasksInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<SyncroItem.Task>> =
        dao.getTasksInRange(startDate.toEpochDay(), endDate.toEpochDay()).toDomain()

    override suspend fun insertTask(task: SyncroItem.Task) {
        require(task.id.isNotBlank()) { "El id de la tarea lo asigna el dominio" }
        dao.insertTask(
            TaskEntity(
                id = task.id,
                remoteId = null,
                title = task.title,
                description = task.description ?: "",
                date = task.date.toEpochDay(),
                time = task.time.toStoredTime(),
                isCompleted = task.isCompleted,
                categoryText = task.categoryText,
                categoryColor = task.categoryColor?.argb,
                pendingChanges = 1,
                seriesId = task.seriesId,
                reminderMinutes = task.reminderMinutes
            )
        )
    }

    override suspend fun getTaskById(taskId: String): SyncroItem.Task? {
        // Una tarea borrada ya no existe para la app, aunque siga en la tabla hasta borrarse en Google
        val entity = dao.getTaskById(taskId)?.takeUnless { it.isDeleted } ?: return null
        val repeats = entity.seriesId?.let { id -> mapOf(id to seriesDao.getById(id)?.toRecurrence()) }.orEmpty()
        return entity.toDomain(repeats)
    }

    override suspend fun toggleTaskCompletion(taskId: String) {
        dao.toggleTaskCompletion(taskId)
    }

    override suspend fun deleteTask(taskId: String) {
        dao.markTaskDeleted(taskId)
    }

    override suspend fun restoreTask(taskId: String): Boolean {
        return dao.restoreTask(taskId) > 0
    }

    override suspend fun moveTask(taskId: String, date: LocalDate) {
        dao.moveTask(taskId, date.toEpochDay())
    }

    override fun getUnfinishedTasksUntil(date: LocalDate): Flow<List<SyncroItem.Task>> =
        dao.getUnfinishedTasksUntil(date.toEpochDay()).toDomain()

    override fun observeTasksWithReminder(from: LocalDate): Flow<List<SyncroItem.Task>> =
        dao.observeWithReminder(from.toEpochDay()).toDomain()

    override suspend fun getTaskIdsInSeries(seriesId: String, from: LocalDate): List<String> =
        dao.getIdsInSeries(seriesId, from.toEpochDay())

    private fun TaskEntity.toDomain(repeats: Map<String, Recurrence?>): SyncroItem.Task {
        return SyncroItem.Task(
            id = id,
            remoteId = remoteId,
            title = title,
            description = description,
            date = LocalDate.ofEpochDay(date),
            time = time.toLocalTimeOrMidnight(),
            isCompleted = isCompleted,
            categoryText = categoryText,
            categoryColor = categoryColor?.let { ArgbColor(it) },
            seriesId = seriesId,
            repeat = seriesId?.let { repeats[it] },
            reminderMinutes = reminderMinutes
        )
    }
}
