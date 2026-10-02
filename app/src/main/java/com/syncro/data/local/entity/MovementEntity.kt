package com.syncro.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un ingreso o gasto. [type] y [category] son los nombres de los enum del dominio; [date] en días
 * desde 1970 (como tareas y eventos) para poder filtrar por mes en SQL.
 */
@Entity(tableName = "movements", indices = [Index("date")])
data class MovementEntity(
    @PrimaryKey val id: String,
    val type: String,
    val amountCents: Long,
    val category: String,
    val date: Long,
    val note: String?,
    val repeatsMonthly: Boolean
)
