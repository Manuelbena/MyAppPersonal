package com.syncro.domain.usecase

import com.syncro.domain.model.BudgetLevel
import com.syncro.domain.model.HomeSavings
import com.syncro.domain.model.budgetStatuses
import com.syncro.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/**
 * El resumen de Ahorros de Inicio para el mes en que cae [today] (de nómina a nómina si hay día de
 * nómina): null si está apagado en Ajustes (lo de fábrica), si no el balance del mes y los
 * presupuestos que van justos o pasados.
 */
class ObserveHomeSavingsUseCase @Inject constructor(
    private val settings: SettingsRepository,
    private val getMonthMovements: GetMonthMovementsUseCase,
    private val getBudgets: GetBudgetsUseCase
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(today: LocalDate): Flow<HomeSavings?> =
        settings.settings
            .map { it.assistant.homeSavingsEnabled }
            .distinctUntilChanged()
            .flatMapLatest { enabled ->
                if (!enabled) {
                    flowOf(null)
                } else {
                    combine(getMonthMovements(today), getBudgets()) { movements, budgets ->
                        HomeSavings(
                            period = movements.period,
                            incomeCents = movements.incomeCents,
                            expenseCents = movements.expenseCents,
                            hasMovements = movements.occurrences.isNotEmpty(),
                            tightBudgets = movements.budgetStatuses(budgets).filter { it.level != BudgetLevel.OK }
                        )
                    }
                }
            }
}
