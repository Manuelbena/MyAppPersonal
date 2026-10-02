package com.syncro.presentation.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.syncro.domain.usecase.GetTimelineUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Redibuja el widget "Tu día" mientras la app está viva: cuando cambian las tareas o eventos de hoy
 * (crear, completar, borrar, sincronizar), cuando cambia el día y cada cuarto de hora para quitar
 * los eventos que ya terminaron. Con la app cerrada, Android lo refresca cada 30 minutos
 * (updatePeriodMillis en today_widget_info.xml).
 */
@Singleton
class TodayWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getTimeline: GetTimelineUseCase,
    private val clock: Clock
) {
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    suspend fun run() {
        val today = ticker().distinctUntilChanged { a, b -> a.toLocalDate() == b.toLocalDate() }
        combine(today.flatMapLatest { getTimeline(it.toLocalDate()) }, ticker()) { items, _ -> items }
            .drop(1) // al arrancar el widget ya se dibuja solo
            .debounce(DEBOUNCE_MS) // varias escrituras seguidas (una sync) → un solo redibujado
            .collect {
                try {
                    TodayWidget().updateAll(context)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Sin widgets en la pantalla o el sistema ocupado: no debe tumbar la app
                    Log.w(TAG, "No se pudo actualizar el widget", e)
                }
            }
    }

    /** La hora actual ahora y cada cuarto de hora en punto (:00, :15, :30, :45). */
    private fun ticker(): Flow<LocalDateTime> = flow {
        while (true) {
            val now = LocalDateTime.now(clock)
            emit(now)
            val next = now.truncatedTo(ChronoUnit.HOURS).plusMinutes((now.minute / 15 + 1) * 15L)
            delay(ChronoUnit.MILLIS.between(now, next).coerceAtLeast(1_000))
        }
    }

    private companion object {
        const val TAG = "TodayWidget"
        const val DEBOUNCE_MS = 1_000L
    }
}
