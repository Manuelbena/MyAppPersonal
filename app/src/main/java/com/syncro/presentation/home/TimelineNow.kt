package com.syncro.presentation.home

import com.syncro.domain.model.DayWindow
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

/** Un punto del timeline cuya hora se conoce: a [minute] del día (0–1440) le corresponde la altura [y] en pantalla. */
data class TimeAnchor(val minute: Int, val y: Float)

/**
 * Altura de la línea "Ahora" sobre el timeline, interpolando entre las [anchors] en el orden en que
 * se ven (inicio/fin de cada evento, huecos, inicio y fin del día). Así la línea cruza por encima de
 * la tarjeta del evento en curso a la altura que toca. Las anclas que van hacia atrás en la hora
 * (eventos solapados) se ignoran. Antes de la primera, se queda en ella; después de la última, igual.
 */
fun nowLineY(anchors: List<TimeAnchor>, nowMinute: Int): Float? {
    val ordered = mutableListOf<TimeAnchor>()
    anchors.forEach { anchor ->
        val last = ordered.lastOrNull()
        if (last == null || (anchor.minute >= last.minute && anchor.y >= last.y)) ordered += anchor
    }
    if (ordered.isEmpty()) return null
    if (nowMinute <= ordered.first().minute) return ordered.first().y
    ordered.zipWithNext().forEach { (a, b) ->
        if (nowMinute <= b.minute) {
            val span = b.minute - a.minute
            return if (span == 0) b.y else a.y + (b.y - a.y) * (nowMinute - a.minute) / span
        }
    }
    return ordered.last().y
}

/** Minuto del día en que empieza [event] en [day] (0 si viene del día anterior). */
fun SyncroItem.Event.startMinuteOn(day: LocalDate): Int =
    if (date.isBefore(day)) 0 else startTime.toSecondOfDay() / 60

/** Minuto del día en que acaba [event] en [day] (1440 si sigue al día siguiente). */
fun SyncroItem.Event.endMinuteOn(day: LocalDate): Int =
    if (endDate.isAfter(day)) MINUTES_PER_DAY else endTime.toSecondOfDay() / 60

const val MINUTES_PER_DAY = 24 * 60

/**
 * Dónde van las marcas de inicio y fin del día entre los [events] del timeline (ya ordenados): el
 * inicio antes del primer evento con hora que empieza a partir de [window].start (arriba del todo si
 * ninguno empieza antes), el fin antes del primero que empieza después de [window].end.
 */
fun dayBoundaryIndices(events: List<SyncroItem.Event>, day: LocalDate, window: DayWindow): Pair<Int, Int> {
    fun startsToday(event: SyncroItem.Event) = !event.isAllDay && event.date == day
    val hasEarly = events.any { startsToday(it) && it.startTime.isBefore(window.start) }
    val startIndex = if (!hasEarly) 0 else events.indexOfFirst { startsToday(it) && !it.startTime.isBefore(window.start) }
        .let { if (it == -1) events.size else it }
    val endIndex = events.indexOfFirst { startsToday(it) && it.startTime.isAfter(window.end) }
        .let { if (it == -1) events.size else it }
    return startIndex to maxOf(startIndex, endIndex)
}
