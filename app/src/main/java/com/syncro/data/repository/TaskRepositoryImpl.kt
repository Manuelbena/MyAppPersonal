package com.syncro.data.repository

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

    override suspend fun insertTask(task: SyncroItem.Task, description: String, date: LocalDate) {
        val dateEpoch = date.toEpochDay()
        dao.insertTask(
            TaskEntity(
                id = "${dateEpoch}_${task.title}_${task.time}",
                title = task.title,
                description = description,
                date = dateEpoch,
                time = task.time,
                isCompleted = task.isCompleted
            )
        )
    }

    override suspend fun toggleTaskCompletion(taskId: String) {
        dao.getTaskById(taskId)?.let { entity ->
            dao.updateTask(entity.copy(isCompleted = !entity.isCompleted))
        }
    }

    private fun TaskEntity.toDomain(): SyncroItem.Task {
        return SyncroItem.Task(
            id = id,
            title = title,
            description = description,
            time = time,
            isCompleted = isCompleted
        )
    }
}
