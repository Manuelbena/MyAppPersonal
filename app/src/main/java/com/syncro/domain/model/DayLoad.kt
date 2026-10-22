package com.syncro.domain.model

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/*
 * Cómo viene un día: eventos que se pisan y días a tope. El asistente lo cuenta el mismo día y,
 * desde la hora de la noche, también el de mañana, para que dé tiempo a mover algo. Sale de la
 * agenda real: al mover o completar un evento el aviso cambia o desaparece.
 */

/** Dos eventos que se pisan, y el rato en que coinciden (dentro del día). */
data class EventClash(val firstTitle: String, val secondTitle: String, val from: LocalTime, val to: LocalTime)

/**
 * Lo que hay que contar de [date]: los solapes y, si va cargado, cuánto.
 *
 * @param isTomorrow si se cuenta la víspera ("Mañana…") en vez del mismo día ("Hoy…")
 * @param busyMinutes el tiempo ocupado por eventos con hora (los solapes cuentan una vez)
 * @param firstStart cuándo empieza el primero y [lastEnd] cuándo acaba el último (dentro del día)
 */
data class DayLoad(
    val date: LocalDate,
    val isTomorrow: Boolean,
    val clashes: List<EventClash>,
    val eventCount: Int,
    val busyMinutes: Long,
    val firstStart: LocalTime,
    val lastEnd: LocalTime
) {
    val isOverloaded: Boolean get() = busyMinutes >= OVERLOAD_MINUTES || eventCount >= OVERLOAD_EVENTS
}

/** Tiempo ocupado a partir del que un día va a tope (una jornada de 8 h con algo más no avisa). */
const val OVERLOAD_MINUTES = 10 * 60L

/** Eventos con hora a partir de los que un día va a tope aunque sean cortos. */
const val OVERLOAD_EVENTS = 6

/**
 * Cómo viene [date] según [events] (puede traer de otros días). Solo cuentan los eventos con hora
 * sin completar: uno de todo el día no ocupa la agenda (un festivo, un cumpleaños). Null si no hay
 * solapes ni va a tope: no hay nada que contar.
 */
fun dayLoadOf(events: List<SyncroItem.Event>, date: LocalDate, today: LocalDate): DayLoad? {
    val dayStart = date.atStartOfDay()
    val dayEnd = date.plusDays(1).atStartOfDay()
    val slots = events
        .filter { !it.isAllDay && !it.isCompleted && it.occursOn(date) }
        .map { Slot(it.title, maxOf(it.date.atTime(it.startTime), dayStart), minOf(it.endDate.atTime(it.endTime), dayEnd)) }
        .filter { it.end.isAfter(it.start) }
        .sortedWith(compareBy({ it.start }, { it.end }))
    if (slots.isEmpty()) return null

    val clashes = buildList {
        slots.forEachIndexed { i, first ->
            slots.drop(i + 1)
                // Ordenados por inicio: en cuanto uno empieza después de que acabe este, los demás también
                .takeWhile { it.start.isBefore(first.end) }
                // El mismo evento dos veces (p. ej. en dos calendarios) no es un solape
                .filterNot { it.title == first.title && it.start == first.start && it.end == first.end }
                .forEach { second -> add(EventClash(first.title, second.title, second.start.toLocalTime(), minOf(first.end, second.end).toLocalTime())) }
        }
    }
    val load = DayLoad(
        date = date,
        isTomorrow = date == today.plusDays(1),
        clashes = clashes,
        eventCount = slots.size,
        busyMinutes = busyMinutes(slots),
        firstStart = slots.first().start.toLocalTime(),
        lastEnd = slots.maxOf { it.end }.toLocalTime()
    )
    return load.takeIf { it.clashes.isNotEmpty() || it.isOverloaded }
}

private data class Slot(val title: String, val start: LocalDateTime, val end: LocalDateTime)

/** Minutos ocupados, juntando los ratos que se pisan (ya ordenados por inicio). */
private fun busyMinutes(slots: List<Slot>): Long {
    var total = 0L
    var start = slots.first().start
    var end = slots.first().end
    slots.drop(1).forEach { slot ->
        if (slot.start.isAfter(end)) {
            total += Duration.between(start, end).toMinutes()
            start = slot.start
            end = slot.end
        } else if (slot.end.isAfter(end)) {
            end = slot.end
        }
    }
    return total + Duration.between(start, end).toMinutes()
}
