package com.syncro.domain.repository

import com.syncro.domain.model.Movement
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

/** Ingresos y gastos. Solo se guardan en el móvil: no se sincronizan con Google ni con nada. */
interface MovementRepository {
    /**
     * Los movimientos que pueden contar en [month]: los de ese mes y los mensuales que empezaron
     * antes. Es un superconjunto; quien lo usa decide con `Movement.dateIn`.
     */
    fun observeForMonth(month: YearMonth): Flow<List<Movement>>
    suspend fun insertMovement(movement: Movement)
    suspend fun deleteMovement(id: String)
}
