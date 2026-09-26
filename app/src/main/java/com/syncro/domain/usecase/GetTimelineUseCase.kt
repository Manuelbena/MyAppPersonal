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
 * Tareas y eventos de un día en una sola lista:
 *  1. Primero lo que es de todo el día (tareas y eventos).
 *  2. Después, por la hora a la que empieza ese día; un evento que viene del día anterior
 *     (21:30 → 01:00) cuenta como si empezara a las 00:00.
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
            (tasks + events.filter { it.occursOn(date) })
                .sortedWith(compareBy<SyncroItem>({ !it.isAllDay() }, { it.startOn(date) }))
        }
    }

    private fun SyncroItem.isAllDay(): Boolean = when (this) {
        is SyncroItem.Task -> isAllDay
        is SyncroItem.Event -> isAllDay
        is SyncroItem.Note -> false
    }

    private fun SyncroItem.startOn(day: LocalDate): LocalTime = when (this) {
        is SyncroItem.Task -> time
        is SyncroItem.Event -> if (date.isBefore(day)) LocalTime.MIDNIGHT else startTime
        is SyncroItem.Note -> LocalTime.MIDNIGHT // No llegan aquí: el timeline solo combina tareas y eventos
    }
}
