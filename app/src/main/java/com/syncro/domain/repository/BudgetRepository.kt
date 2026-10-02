package com.syncro.domain.repository

import com.syncro.domain.model.Budget
import com.syncro.domain.model.MovementCategory
import kotlinx.coroutines.flow.Flow

/** Presupuestos por categoría. Solo se guardan en el móvil, como los movimientos. */
interface BudgetRepository {
    fun observeBudgets(): Flow<List<Budget>>
    /** Crea o cambia el de su categoría (hay uno como mucho por categoría). */
    suspend fun saveBudget(budget: Budget)
    suspend fun deleteBudget(category: MovementCategory)
}
