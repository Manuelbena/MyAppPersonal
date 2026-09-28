package com.syncro.presentation.home

import com.syncro.domain.model.SyncroItem
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/*
 * Reglas del "ahora" en el timeline: cuánto ha avanzado un evento y dónde va la línea de la hora
 * actual. Son funciones puras (reciben `now`) para poder probarlas sin reloj.
 */

/** Inicio y fin reales del evento; en los de todo el día, el fin es la medianoche tras el último día. */
private fun SyncroItem.Event.startDateTime(): LocalDateTime = date.atTime(startTime)
private fun SyncroItem.Event.endDateTime(): LocalDateTime =
    if (isAllDay) endDate.plusDays(1).atStartOfDay() else endDate.atTime(endTime)

/**
 * Parte del evento que ya ha pasado, de 0 (aún no ha empezado) a 1 (ya terminó). Se usa para
 * rellenar el raíl del timeline: así lo pasado se ve lleno y lo que viene, vacío.
 */
fun SyncroItem.Event.progressAt(now: LocalDateTime): Float {
    val start = startDateTime()
    val end = endDateTime()
    return when {
        !now.isAfter(start) -> 0f
        !now.isBefore(end) -> 1f
        else -> Duration.between(start, now).toMillis().toFloat() / Duration.between(start, end).toMillis()
    }
}

/** Minutos que le quedan a un evento en curso, o null si no está en curso ahora mismo. */
fun SyncroItem.Event.minutesLeftAt(now: LocalDateTime): Long? {
    if (isAllDay || !now.isAfter(startDateTime()) || !now.isBefore(endDateTime())) return null
    // Redondeo hacia arriba: con 30 segundos por delante aún "queda 1 min", no 0
    return (Duration.between(now, endDateTime()).seconds + 59) / 60
}

/**
 * Posición de la línea "Ahora" en el timeline de [day] (ya ordenado con `sortedForDay`): antes del
 * primer elemento que empieza después de [now], o al final si ya empezaron todos. Los elementos de
 * todo el día y los que vienen del día anterior cuentan como ya empezados. Null si [day] no es hoy
 * o no hay nada que mostrar.
 */
fun nowIndicatorIndex(items: List<SyncroItem>, day: LocalDate, now: LocalDateTime): Int? {
    if (day != now.toLocalDate() || items.none { it !is SyncroItem.Note }) return null
    val nowTime = now.toLocalTime()
    val index = items.indexOfFirst { item -> startTimeOnDay(item, day)?.isAfter(nowTime) == true }
    return if (index == -1) items.size else index
}

/** Hora a la que empieza el elemento en [day]; null si no tiene hora propia ese día. */
private fun startTimeOnDay(item: SyncroItem, day: LocalDate): LocalTime? = when (item) {
    is SyncroItem.Event -> if (item.isAllDay || item.date != day) null else item.startTime
    is SyncroItem.Task -> if (item.isAllDay) null else item.time
    is SyncroItem.Note -> null
}
