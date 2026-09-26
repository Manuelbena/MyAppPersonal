package com.syncro.domain.usecase

import com.syncro.domain.model.SyncroItem
import com.syncro.domain.repository.EventRepository
import com.syncro.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject

/** Tareas y eventos de un día en una sola lista, ordenados por hora de inicio. */
class GetTimelineUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val eventRepository: EventRepository
) {
    operator fun invoke(date: LocalDate): Flow<List<SyncroItem>> {
        return combine(
            taskRepository.getTasksByDate(date),
            eventRepository.getEventsByDate(date)
        ) { tasks, events ->
            // Las horas son "HH:mm" con ceros a la izquierda, así que el orden alfabético es el cronológico
            (tasks + events).sortedBy { item ->
                when (item) {
                    is SyncroItem.Task -> item.time
                    is SyncroItem.Event -> item.startTime
                    is SyncroItem.Note -> "00:00" // No llegan aquí: el timeline solo combina tareas y eventos
                }
            }
        }
    }
}
