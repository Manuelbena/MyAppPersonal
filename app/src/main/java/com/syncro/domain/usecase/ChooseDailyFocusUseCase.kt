package com.syncro.domain.usecase

import com.syncro.domain.model.DailyFocus
import com.syncro.domain.model.FocusedTask
import com.syncro.domain.model.MAX_FOCUS_TASKS
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.model.TooManyFocusTasksException
import com.syncro.domain.repository.DailyFocusRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Guarda las prioridades del día. Una lista vacía es "Hoy no" (se guarda igual, para no volver a
 * preguntar). Falla sin guardar con [TooManyFocusTasksException] si hay más de [MAX_FOCUS_TASKS].
 */
class ChooseDailyFocusUseCase @Inject constructor(
    private val repository: DailyFocusRepository
) {
    suspend operator fun invoke(date: LocalDate, tasks: List<SyncroItem.Task>): Result<Unit> {
        val unique = tasks.distinctBy { it.id }
        if (unique.size > MAX_FOCUS_TASKS) return Result.failure(TooManyFocusTasksException())
        repository.saveFocus(DailyFocus(date, unique.map { FocusedTask(it.id, it.title) }))
        return Result.success(Unit)
    }
}
