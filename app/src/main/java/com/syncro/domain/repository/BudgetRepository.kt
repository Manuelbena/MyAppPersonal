package com.syncro.domain.repository

import com.syncro.domain.model.Budget
import com.syncro.domain.model.MovementCategory
import kotlinx.coroutines.flow.Flow

/** Presupuestos por categoría y cuenta. Solo se guardan en el móvil, como los movimientos. */
interface BudgetRepository {
    /** Los de la cuenta [accountId], o los de todas con null. */
    fun observeBudgets(accountId: String? = null): Flow<List<Budget>>
    /** Crea o cambia el de su categoría en su cuenta (hay uno como mucho por categoría y cuenta). */
    suspend fun saveBudget(budget: Budget)
    suspend fun deleteBudget(accountId: String, category: MovementCategory)
}
