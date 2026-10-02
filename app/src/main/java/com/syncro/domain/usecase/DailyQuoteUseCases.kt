package com.syncro.domain.usecase

import com.syncro.domain.model.Quote
import com.syncro.domain.repository.DailyQuoteRepository
import com.syncro.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * La frase del día que se muestra en Inicio, o null si no toca: apagada en Ajustes, o cerrada hoy
 * con la ✕ (mañana vuelve, y con otra frase).
 */
class ObserveDailyQuoteUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val quoteRepository: DailyQuoteRepository,
    private val getDailyQuote: GetDailyQuoteUseCase,
    private val clock: Clock
) {
    operator fun invoke(): Flow<Quote?> =
        combine(settingsRepository.settings, quoteRepository.hiddenOn) { settings, hiddenOn ->
            val today = LocalDate.now(clock)
            getDailyQuote().takeIf { settings.assistant.dailyQuoteEnabled && hiddenOn != today }
        }
}

/** La ✕ de la frase: la oculta lo que queda de hoy. */
class HideDailyQuoteUseCase @Inject constructor(
    private val quoteRepository: DailyQuoteRepository,
    private val clock: Clock
) {
    suspend operator fun invoke() = quoteRepository.hide(LocalDate.now(clock))
}
