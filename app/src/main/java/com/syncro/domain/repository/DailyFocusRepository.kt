package com.syncro.domain.repository

import com.syncro.domain.model.DailyFocus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Las prioridades que el usuario elige cada día. Solo en el móvil: Google no tiene nada parecido. */
interface DailyFocusRepository {
    /** Las del día, o null si aún no se han elegido (ni se dijo "Hoy no"). */
    fun getFocus(date: LocalDate): Flow<DailyFocus?>
    /** Las de ese día en adelante, para el historial del chat. */
    fun getFocusSince(date: LocalDate): Flow<List<DailyFocus>>
    suspend fun saveFocus(focus: DailyFocus)
}
