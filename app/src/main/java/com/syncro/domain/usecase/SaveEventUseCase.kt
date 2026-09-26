package com.syncro.domain.usecase

import com.syncro.domain.model.ArgbColor
import com.syncro.domain.model.BlankTitleException
import com.syncro.domain.model.InvalidEventTimeRangeException
import com.syncro.domain.model.Priority
import com.syncro.domain.model.Subtask
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.model.isValidEventTimeRange
import com.syncro.domain.repository.EventRepository
import com.syncro.domain.repository.GoogleSyncRepository
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

class SaveEventUseCase @Inject constructor(
    private val repository: EventRepository,
    private val googleSyncRepository: GoogleSyncRepository
) {
    /**
     * Crea (sin [id]) o actualiza un evento.
     *
     * Falla sin guardar nada con [BlankTitleException] si el título está vacío o con
     * [InvalidEventTimeRangeException] si la hora de fin es anterior a la de inicio.
     * Un fallo al subir a Google no hace fallar el guardado: el evento queda pendiente en local
     * y se sube automáticamente cuando haya conexión.
     */
    suspend operator fun invoke(
        id: String? = null,
        title: String,
        description: String?,
        location: String?,
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
        categoryText: String,
        categoryColor: ArgbColor,
        priority: Priority?,
        subtasks: List<String>
    ): Result<Unit> {
        if (title.isBlank()) return Result.failure(BlankTitleException())
        if (!isValidEventTimeRange(startTime, endTime)) {
            return Result.failure(InvalidEventTimeRangeException())
        }

        // Al editar se conservan el remoteId (para actualizar en Google en vez de duplicar),
        // el estado de completado y el de las subtareas que sigan existiendo
        val existing = id?.let { repository.getEventById(it) }
        val completedSubtasks = existing?.subtasks.orEmpty().filter { it.isCompleted }.map { it.title }.toSet()

        val event = SyncroItem.Event(
            id = id ?: UUID.randomUUID().toString(),
            remoteId = existing?.remoteId,
            title = title.trim(),
            description = description,
            date = date,
            startTime = startTime,
            endTime = endTime,
            categoryText = categoryText,
            categoryColor = categoryColor,
            priority = priority,
            subtasks = subtasks.map { Subtask(it, it in completedSubtasks) },
            isCompleted = existing?.isCompleted ?: false,
            location = location
        )
        repository.insertEvent(event)

        googleSyncRepository.pushEvent(event.id)
        return Result.success(Unit)
    }
}
