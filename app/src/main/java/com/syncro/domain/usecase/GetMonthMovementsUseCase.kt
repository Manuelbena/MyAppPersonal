package com.syncro.domain.usecase

import com.syncro.domain.model.MonthMovements
import com.syncro.domain.model.SavingsPeriod
import com.syncro.domain.model.monthMovements
import com.syncro.domain.model.savingsPeriodOf
import com.syncro.domain.repository.MovementRepository
import com.syncro.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/**
 * Los movimientos del mes en que cae una fecha (con los mensuales que caen en él) y sus totales.
 * El "mes" va de nómina a nómina si hay día de nómina en Ajustes (cobrando el 27: del 27 al 26);
 * si no, es el mes natural. Si se cambia el día de nómina, se recalcula.
 */
class GetMonthMovementsUseCase @Inject constructor(
    private val repository: MovementRepository,
    private val settings: SettingsRepository
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(date: LocalDate): Flow<MonthMovements> =
        period(date).flatMapLatest { period ->
            repository.observeForPeriod(period).map { monthMovements(period, it) }
        }

    /** Solo el periodo en que cae [date], para saber qué mes se ve antes de cargar los movimientos. */
    fun period(date: LocalDate): Flow<SavingsPeriod> =
        settings.settings
            .map { savingsPeriodOf(date, it.assistant.paydayDay) }
            .distinctUntilChanged()
}
