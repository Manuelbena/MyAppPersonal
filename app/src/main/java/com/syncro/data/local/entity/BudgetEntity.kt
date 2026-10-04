package com.syncro.data.local.entity

import androidx.room.Entity

/**
 * Presupuesto mensual de una categoría en una cuenta; [category] es el nombre del enum del dominio
 * (uno por categoría y cuenta).
 */
@Entity(tableName = "budgets", primaryKeys = ["accountId", "category"])
data class BudgetEntity(
    val accountId: String,
    val category: String,
    val limitCents: Long
)
