package com.syncro.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Presupuesto mensual de una categoría; [category] es el nombre del enum del dominio (uno por categoría). */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val category: String,
    val limitCents: Long
)
