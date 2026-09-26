package com.syncro.domain.model

import java.time.LocalDate
import java.time.LocalTime

/** Si el elemento no tiene hora concreta (evento o tarea de todo el día). */
val SyncroItem.isAllDay: Boolean
    get() = when (this) {
        is SyncroItem.Task -> isAllDay
        is SyncroItem.Event -> isAllDay
        is SyncroItem.Note -> false
    }

/**
 * Orden de los elementos de un día, común al timeline de Inicio y al Calendario:
 *  1. Primero lo que es de todo el día (tareas y eventos).
 *  2. Después, por la hora a la que empieza ese día; un evento que viene del día anterior
 *     (21:30 → 01:00) cuenta como si empezara a las 00:00.
 */
fun List<SyncroItem>.sortedForDay(day: LocalDate): List<SyncroItem> =
    sortedWith(compareBy({ !it.isAllDay }, { it.startOn(day) }))

private fun SyncroItem.startOn(day: LocalDate): LocalTime = when (this) {
    is SyncroItem.Task -> time
    is SyncroItem.Event -> if (date.isBefore(day)) LocalTime.MIDNIGHT else startTime
    is SyncroItem.Note -> LocalTime.MIDNIGHT
}
