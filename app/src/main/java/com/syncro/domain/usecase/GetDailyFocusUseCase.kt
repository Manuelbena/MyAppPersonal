package com.syncro.domain.usecase

import com.syncro.domain.model.SyncroItem
import com.syncro.domain.repository.DailyFocusRepository
import com.syncro.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject

/**
 * Las prioridades de un día como tareas actuales, en el orden en que se eligieron. Incluye las ya
 * hechas (para ver el progreso) y quita las que se pasaron a otro día o se borraron.
 */
class GetDailyFocusUseCase @Inject constructor(
    private val focusRepository: DailyFocusRepository,
    private val taskRepository: TaskRepository
) {
    operator fun invoke(date: LocalDate): Flow<List<SyncroItem.Task>> =
        combine(focusRepository.getFocus(date), taskRepository.getTasksByDate(date)) { focus, tasks ->
            val byId = tasks.associateBy { it.id }
            focus?.tasks.orEmpty().mapNotNull { byId[it.id] }
        }
}
