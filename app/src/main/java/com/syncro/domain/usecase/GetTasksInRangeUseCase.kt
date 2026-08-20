package com.syncro.domain.usecase

import com.syncro.domain.model.SyncroItem
import com.syncro.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

class GetTasksInRangeUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    operator fun invoke(startDate: LocalDate, endDate: LocalDate): Flow<List<SyncroItem.Task>> {
        return repository.getTasksInRange(startDate, endDate)
    }
}
