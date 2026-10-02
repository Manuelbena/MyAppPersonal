package com.syncro.domain.model

import java.time.LocalDate

/** La marca de un día en la tira de la semana de Inicio. */
enum class DayMark {
    /** Tiene alguna tarea o evento sin completar. */
    PENDING,

    /** Tiene tareas o eventos y todos están completados. */
    DONE
}

/**
 * Qué días entre [from] y [to] (incluidos) tienen tareas o eventos, y si queda algo por hacer.
 * Los eventos cuentan en todos los días que ocupan ([SyncroItem.Event.days]); los días sin nada
 * no aparecen en el mapa.
 */
fun dayMarks(
    tasks: List<SyncroItem.Task>,
    events: List<SyncroItem.Event>,
    from: LocalDate,
    to: LocalDate
): Map<LocalDate, DayMark> {
    val pendingByDay = mutableMapOf<LocalDate, Boolean>()
    fun mark(day: LocalDate, isCompleted: Boolean) {
        if (day.isBefore(from) || day.isAfter(to)) return
        pendingByDay[day] = (pendingByDay[day] ?: false) || !isCompleted
    }
    tasks.forEach { mark(it.date, it.isCompleted) }
    events.forEach { event -> event.days.forEach { mark(it, event.isCompleted) } }
    return pendingByDay.mapValues { (_, pending) -> if (pending) DayMark.PENDING else DayMark.DONE }
}
