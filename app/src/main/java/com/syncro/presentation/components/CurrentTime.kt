package com.syncro.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Hora actual al minuto, que se actualiza sola cuando cambia el minuto (para la línea "Ahora" y el
 * progreso de los eventos en curso). Espera justo hasta el siguiente minuto en vez de sondear.
 */
@Composable
fun rememberCurrentMinute(): LocalDateTime {
    val now by produceState(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)) {
        while (true) {
            val current = LocalDateTime.now()
            val nextMinute = current.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
            delay(Duration.between(current, nextMinute).toMillis() + 50)
            value = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
        }
    }
    return now
}
