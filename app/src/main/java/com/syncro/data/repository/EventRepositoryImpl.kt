package com.syncro.data.repository

import androidx.compose.ui.graphics.Color
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
        return dao.getEventsByDate(date.toEpochDay()).map { entities ->
            entities.map { entity ->
                SyncroItem.Event(
                    id = entity.id.toString(),
                    title = entity.title,
                    description = entity.description,
                    startTime = entity.startTime,
                    endTime = entity.endTime,
                    categoryText = entity.categoryText,
                    categoryColor = Color(entity.categoryColor),
                    priority = entity.priority?.let { Priority.valueOf(it) }
                    // Subtareas se cargarían asíncronamente si fuera necesario, 
                    // o mediante un join en el DAO
                )
            }
        }
    }

    override suspend fun insertEvent(event: SyncroItem.Event, date: LocalDate) {
        val entity = EventEntity(
            title = event.title,
            description = event.description,
            date = date.toEpochDay(),
            startTime = event.startTime,
            endTime = event.endTime,
            categoryText = event.categoryText,
            categoryColor = event.categoryColor.value.toInt(),
            priority = event.priority?.name
        )
        dao.insertEvent(entity)
    }
}
