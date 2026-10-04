package com.syncro.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Una cuenta de ahorro (solo local). [isActive]: la que se ve en Ahorros e Inicio (una sola). */
@Entity(tableName = "accounts")
data class SavingsAccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Int,
    val position: Int,
    val isActive: Boolean
)
