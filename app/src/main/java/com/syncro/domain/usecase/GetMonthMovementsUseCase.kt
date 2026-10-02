package com.syncro.domain.usecase

import com.syncro.domain.model.MonthMovements
import com.syncro.domain.model.monthMovements
import com.syncro.domain.repository.MovementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject

/** Los movimientos de un mes (con los mensuales que caen en él) y sus totales. */
class GetMonthMovementsUseCase @Inject constructor(
    private val repository: MovementRepository
) {
    operator fun invoke(month: YearMonth): Flow<MonthMovements> =
        repository.observeForMonth(month).map { monthMovements(month, it) }
}
