package com.syncro.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.syncro.data.local.entity.MovementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MovementDao {
    /** Los del rango [start, end] más los mensuales que empezaron antes de [end] (días desde 1970). */
    @Query("SELECT * FROM movements WHERE (date BETWEEN :start AND :end) OR (repeatsMonthly = 1 AND date <= :end)")
    fun observeForRange(start: Long, end: Long): Flow<List<MovementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovement(movement: MovementEntity)

    @Query("DELETE FROM movements WHERE id = :id")
    suspend fun deleteMovement(id: String)

    @Query("SELECT COUNT(*) FROM movements")
    suspend fun count(): Int
}
