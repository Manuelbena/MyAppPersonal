package com.syncro.data.repository

import com.syncro.domain.model.ArgbColor
import com.syncro.data.local.dao.TaskDao
import com.syncro.data.local.entity.TaskEntity
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val dao: TaskDao
) : TaskRepository {

    override fun getTasksByDate(date: LocalDate): Flow<List<SyncroItem.Task>> {
        val epochDay = date.toEpochDay()
        return dao.getTasksByDate(epochDay).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getTasksInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<SyncroItem.Task>> {
        return dao.getTasksInRange(startDate.toEpochDay(), endDate.toEpochDay()).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insertTask(task: SyncroItem.Task) {
        require(task.id.isNotBlank()) { "El id de la tarea lo asigna el dominio" }
        dao.insertTask(
            TaskEntity(
                id = task.id,
                remoteId = null,
                title = task.title,
                description = task.description ?: "",
                date = task.date.toEpochDay(),
                time = task.time,
                isCompleted = task.isCompleted,
                categoryText = task.categoryText,
                categoryColor = task.categoryColor?.argb,
                pendingChanges = 1
            )
        )
    }

    override suspend fun getTaskById(taskId: String): SyncroItem.Task? {
        return dao.getTaskById(taskId)?.toDomain()
    }

    override suspend fun toggleTaskCompletion(taskId: String) {
        dao.toggleTaskCompletion(taskId)
    }

    private fun TaskEntity.toDomain(): SyncroItem.Task {
        return SyncroItem.Task(
            id = id,
            remoteId = remoteId,
            title = title,
            description = description,
            date = LocalDate.ofEpochDay(date),
            time = time,
            isCompleted = isCompleted,
            categoryText = categoryText,
            categoryColor = categoryColor?.let { ArgbColor(it) }
        )
    }
}
