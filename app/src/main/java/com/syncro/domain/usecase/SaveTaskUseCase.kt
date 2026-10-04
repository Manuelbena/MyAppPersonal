package com.syncro.domain.usecase

import com.syncro.domain.model.ArgbColor
import com.syncro.domain.model.BlankTitleException
import com.syncro.domain.model.Recurrence
import com.syncro.domain.model.RepeatSeries
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.model.toSentenceCase
import com.syncro.domain.repository.GoogleSyncRepository
import com.syncro.domain.repository.RepeatSeriesRepository
import com.syncro.domain.repository.TaskRepository
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

class SaveTaskUseCase @Inject constructor(
    private val repository: TaskRepository,
    private val googleSyncRepository: GoogleSyncRepository,
    private val seriesRepository: RepeatSeriesRepository,
    private val generateRepeats: GenerateRepeatsUseCase
) {
    /**
     * Crea una tarea. Falla sin guardar nada con [BlankTitleException] si el título está vacío.
     * Un fallo al subir a Google no hace fallar el guardado: queda pendiente y se sube
     * automáticamente al recuperar la conexión.
     *
     * Con [repeat] se crea una serie que empieza en [date]: sus repeticiones son tareas normales
     * (la primera, la del primer día desde [date] que cumple la regla).
     */
    suspend operator fun invoke(
        title: String,
        description: String,
        date: LocalDate,
        time: LocalTime,
        categoryText: String? = null,
        categoryColor: ArgbColor? = null,
        repeat: Recurrence? = null
    ): Result<Unit> {
        if (title.isBlank()) return Result.failure(BlankTitleException())

        val task = SyncroItem.Task(
            id = UUID.randomUUID().toString(),
            title = title.toSentenceCase(),
            description = description.toSentenceCase(),
            date = date,
            time = time,
            isCompleted = false,
            categoryText = categoryText,
            categoryColor = categoryColor
        )
        if (repeat != null) {
            seriesRepository.saveSeries(
                RepeatSeries(id = UUID.randomUUID().toString(), recurrence = repeat, start = date, generatedUntil = date.minusDays(1), template = task)
            )
            generateRepeats(upload = false)
            googleSyncRepository.pushPendingChanges()
            return Result.success(Unit)
        }
        repository.insertTask(task)

        googleSyncRepository.pushTask(task.id)
        return Result.success(Unit)
    }
}
