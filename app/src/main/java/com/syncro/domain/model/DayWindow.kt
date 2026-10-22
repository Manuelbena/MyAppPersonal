package com.syncro.domain.model

import java.time.LocalTime

/**
 * Inicio y fin del día en el timeline de Inicio: la hora del resumen de la mañana y la de la noche,
 * cada una solo si su aviso está encendido; si no, el día entero (00:00 / 23:59).
 */
data class DayWindow(val start: LocalTime, val end: LocalTime)

val DigestSettings.dayWindow: DayWindow
    get() = DayWindow(
        start = if (morningEnabled) morningTime else LocalTime.MIDNIGHT,
        end = if (eveningEnabled) eveningTime else LocalTime.of(23, 59)
    )
