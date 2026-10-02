package com.syncro.data.repository

import com.syncro.data.local.dao.BudgetDao
import com.syncro.data.local.entity.BudgetEntity
import com.syncro.domain.model.Budget
import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.MovementType
import com.syncro.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class BudgetRepositoryImpl @Inject constructor(
    private val dao: BudgetDao
) : BudgetRepository {

    /** Una categoría que ya no existe (o de ingresos) se ignora en vez de romper la lista. */
    override fun observeBudgets(): Flow<List<Budget>> = dao.observeBudgets().map { entities ->
        entities.mapNotNull { entity ->
            MovementCategory.entries
                .firstOrNull { it.name == entity.category && it.type == MovementType.EXPENSE }
                ?.let { Budget(it, entity.limitCents) }
        }
    }

    override suspend fun saveBudget(budget: Budget) =
        dao.saveBudget(BudgetEntity(budget.category.name, budget.limitCents))

    override suspend fun deleteBudget(category: MovementCategory) = dao.deleteBudget(category.name)
}
