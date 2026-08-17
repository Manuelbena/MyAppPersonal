package com.syncro.domain.usecase

import com.syncro.domain.model.SyncroItem
import com.syncro.domain.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

class GetEventsInRangeUseCase @Inject constructor(
    private val repository: EventRepository
) {
    operator fun invoke(startDate: LocalDate, endDate: LocalDate): Flow<List<SyncroItem.Event>> {
        return repository.getEventsInRange(startDate, endDate)
    }
}
