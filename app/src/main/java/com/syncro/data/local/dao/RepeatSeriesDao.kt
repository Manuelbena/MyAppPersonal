package com.syncro.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.syncro.data.local.entity.RepeatSeriesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RepeatSeriesDao {
    @Query("SELECT * FROM repeat_series")
    suspend fun getAll(): List<RepeatSeriesEntity>

    /** Para mostrar cómo se repite cada tarea o evento (son pocas: se observan todas). */
    @Query("SELECT * FROM repeat_series")
    fun observeAll(): Flow<List<RepeatSeriesEntity>>

    @Query("SELECT * FROM repeat_series WHERE id = :id")
    suspend fun getById(id: String): RepeatSeriesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(series: RepeatSeriesEntity)

    @Query("DELETE FROM repeat_series WHERE id = :id")
    suspend fun delete(id: String)
}
