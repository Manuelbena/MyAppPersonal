package com.syncro.domain.usecase

import com.syncro.domain.model.SyncroItem
import com.syncro.domain.repository.EventRepository
import com.syncro.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/**
 * Tareas y eventos de un día en una sola lista, ordenados por la hora a la que empiezan ese día.
 * Un evento que viene del día anterior (21:30 → 01:00) aparece al principio del día siguiente.
 */
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
            (tasks + events.filter { it.occursOn(date) }).sortedBy { item -> item.startOn(date) }
        }
    }

    private fun SyncroItem.startOn(day: LocalDate): LocalTime = when (this) {
        is SyncroItem.Task -> time
        is SyncroItem.Event -> if (date.isBefore(day)) LocalTime.MIDNIGHT else startTime
        is SyncroItem.Note -> LocalTime.MIDNIGHT // No llegan aquí: el timeline solo combina tareas y eventos
    }
}
