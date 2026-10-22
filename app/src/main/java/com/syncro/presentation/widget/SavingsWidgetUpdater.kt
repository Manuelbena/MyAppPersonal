package com.syncro.presentation.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.syncro.domain.usecase.ObserveSavingsSummaryUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Redibuja el widget "Ahorros" mientras la app está viva: al apuntar, editar o borrar un
 * movimiento, al cambiar de cuenta, presupuestos o día de nómina, y al cambiar de día (puede
 * empezar otro mes). Con la app cerrada, Android lo refresca cada 30 minutos.
 */
@Singleton
class SavingsWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val observeSavingsSummary: ObserveSavingsSummaryUseCase,
    private val clock: Clock
) {
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    suspend fun run() {
        days()
            .flatMapLatest { observeSavingsSummary(it) }
            .distinctUntilChanged()
            .drop(1) // al arrancar el widget ya se dibuja solo
            .debounce(DEBOUNCE_MS)
            .collect {
                try {
                    SavingsWidget().updateAll(context)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Sin widgets en la pantalla o el sistema ocupado: no debe tumbar la app
                    Log.w(TAG, "No se pudo actualizar el widget", e)
                }
            }
    }

    /** El día de hoy ahora y cada medianoche. */
    private fun days(): Flow<LocalDate> = flow {
        while (true) {
            val now = LocalDateTime.now(clock)
            emit(now.toLocalDate())
            val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
            delay(ChronoUnit.MILLIS.between(now, midnight).coerceAtLeast(1_000))
        }
    }

    private companion object {
        const val TAG = "SavingsWidget"
        const val DEBOUNCE_MS = 1_000L
    }
}
