package com.syncro.domain.usecase

import com.syncro.domain.model.DailyFocus
import com.syncro.domain.repository.DailyFocusRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

/** Las prioridades elegidas desde [since] (incluido), para el historial del chat. */
class GetFocusHistoryUseCase @Inject constructor(
    private val repository: DailyFocusRepository
) {
    operator fun invoke(since: LocalDate): Flow<List<DailyFocus>> = repository.getFocusSince(since)
}
