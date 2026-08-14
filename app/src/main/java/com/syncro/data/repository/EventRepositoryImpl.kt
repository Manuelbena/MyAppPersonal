package com.syncro.data.repository

import androidx.compose.ui.graphics.Color
import com.syncro.data.local.dao.EventDao
import com.syncro.data.local.entity.EventEntity
import com.syncro.domain.model.Priority
import com.syncro.domain.model.Subtask
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class EventRepositoryImpl @Inject constructor(
    private val dao: EventDao
) : EventRepository {

    override fun getEventsByDate(date: LocalDate): Flow<List<SyncroItem.Event>> {
        return dao.getEventsByDate(date.toEpochDay()).map { entities ->
            entities.map { entity ->
                val subtasks = dao.getSubtasksForEvent(entity.id).map {
                    Subtask(title = it.title, isCompleted = it.isCompleted)
                }
                
                SyncroItem.Event(
                    id = entity.id,
                    title = entity.title,
                    description = entity.description,
                    startTime = entity.startTime,
                    endTime = entity.endTime,
                    categoryText = entity.categoryText,
                    categoryColor = Color(entity.categoryColor),
                    priority = entity.priority?.let { Priority.valueOf(it) },
                    subtasks = subtasks,
                    isCompleted = entity.isCompleted
                )
            }
        }
    }

    override suspend fun insertEvent(event: SyncroItem.Event, date: LocalDate) {
        val dateEpoch = date.toEpochDay()
        val entity = EventEntity(
            id = "${dateEpoch}_${event.title}_${event.startTime}",
            title = event.title,
            description = event.description,
            date = dateEpoch,
            startTime = event.startTime,
            endTime = event.endTime,
            categoryText = event.categoryText,
            categoryColor = event.categoryColor.value.toInt(),
            priority = event.priority?.name,
            isCompleted = event.isCompleted
        )
        dao.insertEvent(entity)
    }

    override suspend fun toggleSubtaskCompletion(eventId: String, subtaskTitle: String) {
        dao.toggleSubtaskCompletion(eventId, subtaskTitle)
    }

    override suspend fun toggleEventCompletion(eventId: String) {
        dao.toggleEventCompletion(eventId)
    }
}
