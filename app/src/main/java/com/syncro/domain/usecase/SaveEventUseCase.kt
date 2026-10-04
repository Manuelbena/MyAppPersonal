package com.syncro.domain.usecase

import com.syncro.domain.model.ArgbColor
import com.syncro.domain.model.BlankTitleException
import com.syncro.domain.model.InvalidEventTimeRangeException
import com.syncro.domain.model.Priority
import com.syncro.domain.model.Recurrence
import com.syncro.domain.model.RepeatScope
import com.syncro.domain.model.RepeatSeries
import com.syncro.domain.model.Subtask
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.model.isValidEventRange
import com.syncro.domain.model.toSentenceCase
import com.syncro.domain.repository.EventRepository
import com.syncro.domain.repository.GoogleSyncRepository
import com.syncro.domain.repository.RepeatSeriesRepository
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

class SaveEventUseCase @Inject constructor(
    private val repository: EventRepository,
    private val googleSyncRepository: GoogleSyncRepository,
    private val seriesRepository: RepeatSeriesRepository,
    private val generateRepeats: GenerateRepeatsUseCase
) {
    /**
     * Crea (sin [id]) o actualiza un evento.
     *
     * Falla sin guardar nada con [BlankTitleException] si el título está vacío o con
     * [InvalidEventTimeRangeException] si termina antes de empezar (se comparan fecha y hora).
     * Un fallo al subir a Google no hace fallar el guardado: el evento queda pendiente en local
     * y se sube automáticamente cuando haya conexión.
     *
     * Repeticiones ([repeat]): uno nuevo crea una serie desde [date]; uno suelto que pasa a
     * repetirse es la primera repetición de su serie. Al editar una repetición, [scope] dice si el
     * cambio es solo para ella ([RepeatScope.THIS]: se queda en su serie) o también para las
     * siguientes: la serie vieja acaba el día antes, sus repeticiones siguientes se borran y, si
     * sigue repitiéndose, este evento empieza una serie nueva con los datos y la regla nuevos.
     */
    suspend operator fun invoke(
        id: String? = null,
        title: String,
        description: String?,
        location: String?,
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
        // Por defecto el mismo día; distinto en eventos que cruzan la medianoche
        endDate: LocalDate = date,
        categoryText: String,
        categoryColor: ArgbColor,
        priority: Priority?,
        subtasks: List<String>,
        repeat: Recurrence? = null,
        scope: RepeatScope = RepeatScope.THIS
    ): Result<Unit> {
        if (title.isBlank()) return Result.failure(BlankTitleException())
        if (!isValidEventRange(date.atTime(startTime), endDate.atTime(endTime))) {
            return Result.failure(InvalidEventTimeRangeException())
        }

        // Al editar se conservan el remoteId (para actualizar en Google en vez de duplicar),
        // el estado de completado y el de las subtareas que sigan existiendo
        val existing = id?.let { repository.getEventById(it) }
        // Sin distinguir mayúsculas: una subtarea "comprar pan" de Google que ahora se guarda como
        // "Comprar pan" sigue siendo la misma y no debe perder su check
        val completedSubtasks = existing?.subtasks.orEmpty().filter { it.isCompleted }.map { it.title.lowercase() }.toSet()

        val event = SyncroItem.Event(
            id = id ?: UUID.randomUUID().toString(),
            remoteId = existing?.remoteId,
            title = title.toSentenceCase(),
            description = description?.toSentenceCase(),
            date = date,
            endDate = endDate,
            startTime = startTime,
            endTime = endTime,
            categoryText = categoryText,
            categoryColor = categoryColor,
            priority = priority,
            subtasks = subtasks.map { it.toSentenceCase() }.filter { it.isNotEmpty() }
                .map { Subtask(it, it.lowercase() in completedSubtasks) },
            isCompleted = existing?.isCompleted ?: false,
            location = location?.toSentenceCase(),
            seriesId = existing?.seriesId
        )

        val oldSeriesId = existing?.seriesId
        when {
            // Nuevo y repetido: solo la serie; sus repeticiones (también la primera) las crea el generador
            existing == null && repeat != null -> {
                startSeries(event, repeat, includeEvent = false)
                return uploadPending()
            }
            // Cambio de esta y las siguientes: se cierra la serie vieja desde el día que tenía este
            existing != null && oldSeriesId != null && scope == RepeatScope.THIS_AND_FOLLOWING -> {
                seriesRepository.endBefore(oldSeriesId, existing.date)
                repository.getEventIdsInSeries(oldSeriesId, existing.date)
                    .filter { it != event.id }
                    .forEach { repository.deleteEvent(it) }
                if (repeat != null) startSeries(event, repeat, includeEvent = true)
                else repository.insertEvent(event.copy(seriesId = null))
                return uploadPending()
            }
            // Uno suelto que pasa a repetirse: es la primera repetición
            oldSeriesId == null && repeat != null -> {
                startSeries(event, repeat, includeEvent = true)
                return uploadPending()
            }
        }
        repository.insertEvent(event)

        googleSyncRepository.pushEvent(event.id)
        return Result.success(Unit)
    }

    /**
     * Crea una serie que empieza el día de [event] con él como plantilla. Con [includeEvent], el
     * propio evento es la primera repetición (aunque ese día no cumpla la regla: es el que ya había).
     */
    private suspend fun startSeries(event: SyncroItem.Event, repeat: Recurrence, includeEvent: Boolean) {
        val seriesId = UUID.randomUUID().toString()
        val first = event.copy(seriesId = seriesId, repeat = repeat)
        seriesRepository.saveSeries(
            RepeatSeries(
                id = seriesId,
                recurrence = repeat,
                start = event.date,
                generatedUntil = if (includeEvent) event.date else event.date.minusDays(1),
                template = first
            )
        )
        if (includeEvent) repository.insertEvent(first)
        generateRepeats(upload = false)
    }

    private suspend fun uploadPending(): Result<Unit> {
        googleSyncRepository.pushPendingChanges()
        return Result.success(Unit)
    }
}
