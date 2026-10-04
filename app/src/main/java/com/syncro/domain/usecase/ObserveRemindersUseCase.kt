package com.syncro.domain.usecase

import com.syncro.domain.model.DueReminder
import com.syncro.domain.model.dueReminders
import com.syncro.domain.repository.EventRepository
import com.syncro.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Los avisos de tareas y eventos que aún pueden sonar (de ayer en adelante, sin completar ni
 * borrar), los más próximos primero. Cambia con cada tarea o evento que se crea, edita, completa o
 * borra: la app vuelve a programar la alarma del siguiente cada vez. Solo lee Room: funciona sin
 * conexión.
 */
class ObserveRemindersUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val eventRepository: EventRepository,
    private val clock: Clock
) {
    operator fun invoke(): Flow<List<DueReminder>> {
        // Desde ayer: un aviso de "el día antes" de un evento de hoy ya pasó, pero uno de ayer a
        // las 23:50 aún puede estar a punto de sonar si la app se abre justo a medianoche
        val from = LocalDate.now(clock).minusDays(1)
        return combine(
            taskRepository.observeTasksWithReminder(from),
            eventRepository.observeEventsWithReminder(from)
        ) { tasks, events -> dueReminders(tasks, events) }
            .distinctUntilChanged()
    }
}
