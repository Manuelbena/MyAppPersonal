package com.syncro.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.syncro.data.local.entity.SavingsAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsAccountDao {
    @Query("SELECT * FROM accounts ORDER BY position, name")
    fun observeAll(): Flow<List<SavingsAccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY position, name")
    suspend fun getAll(): List<SavingsAccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): SavingsAccountEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM accounts")
    suspend fun nextPosition(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: SavingsAccountEntity)

    /** Deja marcada solo [id] como la que se ve. */
    @Query("UPDATE accounts SET isActive = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setActive(id: String)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccountRow(id: String)

    @Query("DELETE FROM movements WHERE accountId = :id")
    suspend fun deleteMovementsOf(id: String)

    @Query("DELETE FROM budgets WHERE accountId = :id")
    suspend fun deleteBudgetsOf(id: String)

    /** La cuenta con todo lo suyo (movimientos y presupuestos), de una vez. */
    @Transaction
    suspend fun deleteWithContents(id: String) {
        deleteMovementsOf(id)
        deleteBudgetsOf(id)
        deleteAccountRow(id)
    }
}
