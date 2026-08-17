package com.syncro.data.repository

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.syncro.data.local.dao.EventDao
import com.syncro.data.local.entity.EventEntity
import com.syncro.data.local.entity.SubtaskEntity
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
        return dao.getEventsByDate(date.toEpochDay()).map { relations ->
            relations.map { relation ->
                val entity = relation.event
                val subtasks = relation.subtasks.map {
                    Subtask(title = it.title, isCompleted = it.isCompleted)
                }
                
                SyncroItem.Event(
                    id = entity.id,
                    title = entity.title,
                    description = entity.description,
                    date = LocalDate.ofEpochDay(entity.date),
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

    override fun getEventsInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<SyncroItem.Event>> {
        return dao.getEventsInRange(startDate.toEpochDay(), endDate.toEpochDay()).map { relations ->
            relations.map { relation ->
                val entity = relation.event
                val subtasks = relation.subtasks.map {
                    Subtask(title = it.title, isCompleted = it.isCompleted)
                }

                SyncroItem.Event(
                    id = entity.id,
                    title = entity.title,
                    description = entity.description,
                    date = LocalDate.ofEpochDay(entity.date),
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

    override suspend fun insertEvent(event: SyncroItem.Event, date: LocalDate, location: String?) {
        val dateEpoch = date.toEpochDay()
        val eventId = "${dateEpoch}_${event.title}_${event.startTime}"
        val entity = EventEntity(
            id = eventId,
            title = event.title,
            description = event.description,
            date = dateEpoch,
            startTime = event.startTime,
            endTime = event.endTime,
            categoryText = event.categoryText,
            categoryColor = event.categoryColor.toArgb(),
            priority = event.priority?.name,
            location = location,
            isCompleted = event.isCompleted
        )
        
        if (event.subtasks.isEmpty()) {
            dao.insertEvent(entity)
        } else {
            val subtaskEntities = event.subtasks.map { 
                SubtaskEntity(eventId = eventId, title = it.title, isCompleted = it.isCompleted)
            }
            dao.insertEventWithSubtasks(entity, subtaskEntities)
        }
    }

    override suspend fun toggleSubtaskCompletion(eventId: String, subtaskTitle: String) {
        dao.toggleSubtaskCompletion(eventId, subtaskTitle)
    }

    override suspend fun toggleEventCompletion(eventId: String) {
        dao.toggleEventCompletion(eventId)
    }
}
