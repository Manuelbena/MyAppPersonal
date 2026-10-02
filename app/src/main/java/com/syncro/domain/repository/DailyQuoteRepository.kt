package com.syncro.domain.repository

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Si el usuario cerró la frase del día (con la ✕) y qué día lo hizo. */
interface DailyQuoteRepository {
    /** El último día en que se ocultó la frase; null si nunca. */
    val hiddenOn: Flow<LocalDate?>
    suspend fun hide(date: LocalDate)
}
