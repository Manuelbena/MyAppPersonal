package com.syncro.domain.usecase

import com.syncro.domain.model.ArgbColor
import com.syncro.domain.model.BlankTitleException
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.repository.GoogleSyncRepository
import com.syncro.domain.repository.TaskRepository
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

class SaveTaskUseCase @Inject constructor(
    private val repository: TaskRepository,
    private val googleSyncRepository: GoogleSyncRepository
) {
    /**
     * Crea una tarea. Falla sin guardar nada con [BlankTitleException] si el título está vacío.
     * Un fallo al subir a Google no hace fallar el guardado: queda pendiente y se sube
     * automáticamente al recuperar la conexión.
     */
    suspend operator fun invoke(
        title: String,
        description: String,
        date: LocalDate,
        time: String,
        categoryText: String? = null,
        categoryColor: ArgbColor? = null
    ): Result<Unit> {
        if (title.isBlank()) return Result.failure(BlankTitleException())

        val task = SyncroItem.Task(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            description = description,
            date = date,
            time = time,
            isCompleted = false,
            categoryText = categoryText,
            categoryColor = categoryColor
        )
        repository.insertTask(task)

        googleSyncRepository.pushTask(task.id)
        return Result.success(Unit)
    }
}
