package com.syncro.domain.model

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/*
 * Huecos libres del timeline: el tiempo entre un evento con hora y el siguiente, para ver de un
 * vistazo lo que cabe en el día (no solo lo que hay) y llenarlo con un toque.
 */

data class FreeGap(val start: LocalTime, val end: LocalTime) {
    val minutes: Long get() = Duration.between(start, end).toMinutes()
}

/** Menos que esto no es un hueco: es el rato de ir de un sitio a otro. */
const val MIN_FREE_GAP_MINUTES = 30L

/**
 * El hueco libre antes de cada evento de [events] (en el orden del timeline, `sortedForDay`), por
 * su posición en la lista. Solo entre eventos con hora: antes del primero y después del último el
 * día está "libre" sin más. Mirando hoy, lo ya pasado no cuenta: el hueco empieza [now].
 */
fun freeGapsBefore(events: List<SyncroItem.Event>, day: LocalDate, now: LocalDateTime? = null): Map<Int, FreeGap> {
    val dayStart = day.atStartOfDay()
    val dayEnd = day.plusDays(1).atStartOfDay()
    var busyUntil: LocalDateTime? = null
    val gaps = mutableMapOf<Int, FreeGap>()
    events.forEachIndexed { index, event ->
        if (event.isAllDay) return@forEachIndexed
        val start = maxOf(event.date.atTime(event.startTime), dayStart)
        val end = minOf(event.endDate.atTime(event.endTime), dayEnd)
        busyUntil?.let { previousEnd ->
            val from = if (now != null && now.toLocalDate() == day) maxOf(previousEnd, now) else previousEnd
            if (Duration.between(from, start).toMinutes() >= MIN_FREE_GAP_MINUTES) {
                gaps[index] = FreeGap(from.toLocalTime(), start.toLocalTime())
            }
        }
        busyUntil = busyUntil?.let { maxOf(it, end) } ?: end
    }
    return gaps
}
