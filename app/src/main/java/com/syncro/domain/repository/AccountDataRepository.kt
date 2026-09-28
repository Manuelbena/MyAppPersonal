package com.syncro.domain.repository

import com.syncro.domain.model.DataLossSummary

/**
 * Los datos de la cuenta guardados en el móvil (tareas, eventos, notas, chat del asistente,
 * prioridades). Se borran al cerrar sesión para que no se mezclen con los de otra cuenta.
 */
interface AccountDataRepository {
    /** Lo que se perdería de verdad al borrar (lo demás está en Google y vuelve al entrar). */
    suspend fun dataLossSummary(): DataLossSummary
    suspend fun clearAccountData()
}
