package com.syncro.domain.model

import java.time.LocalDateTime

/**
 * "Lo próximo" de hoy: el evento en curso, el siguiente que empieza hoy y cómo van las tareas.
 * Los eventos de todo el día y los completados no cuentan: no tienen un "ahora" ni un "después".
 */
data class NextUp(
    val current: SyncroItem.Event?,
    val next: SyncroItem.Event?,
    val tasksDone: Int,
    val tasksTotal: Int
)

private val SyncroItem.Event.startDateTime: LocalDateTime get() = date.atTime(startTime)
private val SyncroItem.Event.endDateTime: LocalDateTime get() = endDate.atTime(endTime)

/**
 * Calcula [NextUp] a partir de lo de hoy ([dayItems], los elementos del día de [now]). Si hay
 * varios eventos en curso, se muestra el que antes termina. Null si no hay nada que contar
 * (ni eventos pendientes ni tareas).
 */
fun nextUp(dayItems: List<SyncroItem>, now: LocalDateTime): NextUp? {
    val today = now.toLocalDate()
    val events = dayItems.filterIsInstance<SyncroItem.Event>()
        .filter { !it.isCompleted && !it.isAllDay && it.occursOn(today) }
    val current = events
        .filter { !it.startDateTime.isAfter(now) && it.endDateTime.isAfter(now) }
        .minByOrNull { it.endDateTime }
    val next = events
        .filter { it.startDateTime.isAfter(now) && it.date == today }
        .minByOrNull { it.startDateTime }
    val tasks = dayItems.filterIsInstance<SyncroItem.Task>().filter { it.date == today }
    if (current == null && next == null && tasks.isEmpty()) return null
    return NextUp(current = current, next = next, tasksDone = tasks.count { it.isCompleted }, tasksTotal = tasks.size)
}
