package com.syncro.domain.usecase

import com.syncro.domain.model.Budget
import com.syncro.domain.model.InvalidBudgetException
import com.syncro.domain.model.MAIN_ACCOUNT_ID
import com.syncro.domain.model.MAX_AMOUNT_CENTS
import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.MovementType
import com.syncro.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Los presupuestos de una cuenta de ahorro o, con [accountId] null, los de todas. */
class GetBudgetsUseCase @Inject constructor(
    private val repository: BudgetRepository
) {
    operator fun invoke(accountId: String? = null): Flow<List<Budget>> = repository.observeBudgets(accountId)
}

class SaveBudgetUseCase @Inject constructor(
    private val repository: BudgetRepository
) {
    /**
     * Crea o cambia el presupuesto de una categoría en una cuenta. Falla sin guardar con
     * [InvalidBudgetException] si la categoría es de ingresos (un límite de "Nómina" no tiene
     * sentido) o el límite no es mayor que 0.
     */
    suspend operator fun invoke(category: MovementCategory, limitCents: Long, accountId: String = MAIN_ACCOUNT_ID): Result<Unit> {
        if (category.type != MovementType.EXPENSE) {
            return Result.failure(InvalidBudgetException("Los presupuestos son para gastos"))
        }
        if (limitCents !in 1..MAX_AMOUNT_CENTS) {
            return Result.failure(InvalidBudgetException("Introduce un límite mayor que 0"))
        }
        repository.saveBudget(Budget(category, limitCents, accountId))
        return Result.success(Unit)
    }
}

class DeleteBudgetUseCase @Inject constructor(
    private val repository: BudgetRepository
) {
    suspend operator fun invoke(category: MovementCategory, accountId: String = MAIN_ACCOUNT_ID) = repository.deleteBudget(accountId, category)
}
