package com.syncro.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.syncro.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    /** Los de la cuenta [accountId], o los de todas si es null. */
    @Query("SELECT * FROM budgets WHERE :accountId IS NULL OR accountId = :accountId")
    fun observeBudgets(accountId: String?): Flow<List<BudgetEntity>>

    /** REPLACE: guardar el de una categoría que ya tiene presupuesto en esa cuenta lo cambia. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBudget(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE accountId = :accountId AND category = :category")
    suspend fun deleteBudget(accountId: String, category: String)
}
