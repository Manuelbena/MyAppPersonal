package com.syncro.domain.usecase

import com.syncro.domain.model.SyncroItem
import com.syncro.domain.model.sortedForDay
import com.syncro.domain.repository.EventRepository
import com.syncro.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject

/** Tareas y eventos de un día en una sola lista, en el orden de [sortedForDay]. */
class GetTimelineUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val eventRepository: EventRepository
) {
    operator fun invoke(date: LocalDate): Flow<List<SyncroItem>> {
        return combine(
            taskRepository.getTasksByDate(date),
            eventRepository.getEventsByDate(date)
        ) { tasks, events ->
            // El repositorio devuelve los eventos que tocan el día; la regla exacta es del dominio
            (tasks + events.filter { it.occursOn(date) }).sortedForDay(date)
        }
    }
}
