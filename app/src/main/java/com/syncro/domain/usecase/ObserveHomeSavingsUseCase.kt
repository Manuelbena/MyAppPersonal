package com.syncro.domain.usecase

import com.syncro.domain.model.HomeSavings
import com.syncro.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/**
 * El resumen de Ahorros de Inicio ([ObserveSavingsSummaryUseCase]): null si está apagado en
 * Ajustes (lo de fábrica).
 */
class ObserveHomeSavingsUseCase @Inject constructor(
    private val settings: SettingsRepository,
    private val observeSavingsSummary: ObserveSavingsSummaryUseCase
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(today: LocalDate): Flow<HomeSavings?> =
        settings.settings
            .map { it.assistant.homeSavingsEnabled }
            .distinctUntilChanged()
            .flatMapLatest { enabled -> if (enabled) observeSavingsSummary(today) else flowOf(null) }
}
