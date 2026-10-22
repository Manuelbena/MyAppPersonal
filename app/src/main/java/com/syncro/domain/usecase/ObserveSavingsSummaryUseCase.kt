package com.syncro.domain.usecase

import com.syncro.domain.model.BudgetLevel
import com.syncro.domain.model.HomeSavings
import com.syncro.domain.model.budgetStatuses
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate
import javax.inject.Inject

/**
 * Cómo va el mes en que cae [today] (de nómina a nómina si hay día de nómina) en la cuenta que se
 * está viendo en Ahorros: el balance y los presupuestos que van justos o pasados. Lo usan la
 * tarjeta de Inicio (si está activada) y el widget de Ahorros.
 */
class ObserveSavingsSummaryUseCase @Inject constructor(
    private val getMonthMovements: GetMonthMovementsUseCase,
    private val getBudgets: GetBudgetsUseCase,
    private val observeAccounts: ObserveSavingsAccountsUseCase
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(today: LocalDate): Flow<HomeSavings> =
        observeAccounts().flatMapLatest { accounts ->
            val accountId = accounts.active.id
            combine(getMonthMovements(today, accountId), getBudgets(accountId)) { movements, budgets ->
                HomeSavings(
                    period = movements.period,
                    incomeCents = movements.incomeCents,
                    expenseCents = movements.expenseCents,
                    hasMovements = movements.occurrences.isNotEmpty(),
                    tightBudgets = movements.budgetStatuses(budgets).filter { it.level != BudgetLevel.OK },
                    accountName = accounts.active.name.takeIf { accounts.hasSeveral }
                )
            }
        }
}
