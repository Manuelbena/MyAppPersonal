package com.syncro.domain.usecase

import androidx.compose.ui.graphics.Color
import com.syncro.domain.model.InvalidEventTimeRangeException
import com.syncro.domain.model.Priority
import com.syncro.domain.model.Subtask
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.model.isValidEventTimeRange
import com.syncro.domain.repository.EventRepository
import com.syncro.domain.repository.GoogleSyncRepository
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

class SaveEventUseCase @Inject constructor(
    private val repository: EventRepository,
    private val googleSyncRepository: GoogleSyncRepository
) {
    /**
     * Crea o actualiza un evento. Falla con [InvalidEventTimeRangeException] si la hora de fin es
     * anterior a la de inicio. Un fallo al subir a Google no hace fallar el guardado: el evento
     * queda en local y se reintentará en la siguiente sincronización.
     */
    suspend operator fun invoke(
        id: String? = null,
        title: String,
        description: String?,
        location: String?,
        date: LocalDate,
        startTime: String,
        endTime: String,
        categoryText: String,
        categoryColor: Color,
        priority: Priority?,
        subtasks: List<String>
    ): Result<Unit> {
        if (!isValidEventTimeRange(LocalTime.parse(startTime), LocalTime.parse(endTime))) {
            return Result.failure(InvalidEventTimeRangeException())
        }

        // Al editar se conservan el remoteId (para actualizar en Google en vez de duplicar),
        // el estado de completado y el de las subtareas que sigan existiendo
        val existing = id?.let { repository.getEventById(it) }
        val completedSubtasks = existing?.subtasks.orEmpty().filter { it.isCompleted }.map { it.title }.toSet()

        val event = SyncroItem.Event(
            id = id ?: "0", // Generated in repository if "0"
            remoteId = existing?.remoteId,
            title = title,
            description = description,
            date = date,
            startTime = startTime,
            endTime = endTime,
            categoryText = categoryText,
            categoryColor = categoryColor,
            priority = priority,
            subtasks = subtasks.map { Subtask(it, it in completedSubtasks) },
            isCompleted = existing?.isCompleted ?: false
        )
        val eventId = repository.insertEvent(event, date, location)

        googleSyncRepository.uploadEventToGoogle(
            eventId = eventId,
            title = title,
            description = description,
            location = location,
            startDate = date,
            startTime = startTime,
            endTime = endTime,
            category = categoryText,
            subtasks = subtasks
        )
        return Result.success(Unit)
    }
}
