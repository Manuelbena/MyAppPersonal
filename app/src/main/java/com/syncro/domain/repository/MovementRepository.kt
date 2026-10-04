package com.syncro.domain.repository

import com.syncro.domain.model.Movement
import com.syncro.domain.model.SavingsPeriod
import kotlinx.coroutines.flow.Flow

/** Ingresos y gastos. Solo se guardan en el móvil: no se sincronizan con Google ni con nada. */
interface MovementRepository {
    /**
     * Los movimientos que pueden contar en [period]: los de esas fechas y los mensuales que
     * empezaron antes, de la cuenta [accountId] (null = de todas). Es un superconjunto; quien lo
     * usa decide con `Movement.datesIn`.
     */
    fun observeForPeriod(period: SavingsPeriod, accountId: String? = null): Flow<List<Movement>>
    /** Todos, para exportarlos o guardarlos en una copia. */
    suspend fun getAllMovements(): List<Movement>
    suspend fun insertMovement(movement: Movement)
    suspend fun deleteMovement(id: String)
}
