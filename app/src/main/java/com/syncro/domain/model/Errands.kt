package com.syncro.domain.model

import java.time.LocalDate

/*
 * Recados del día: si hoy hay varias salidas (Recados o Compras) sin hacer, el asistente propone
 * juntarlas en una. Sale de la agenda real, así que al completarlas el mensaje deja de salir.
 */

/** Las salidas pendientes de [date], en orden del día. */
data class ErrandsDay(val date: LocalDate, val titles: List<String>)

private val ERRAND_CATEGORIES = setOf("Recados", "Compras")

/** Las salidas pendientes de [date], o null si hay menos de dos (una sola no hay con qué juntarla). */
fun errandsOf(events: List<SyncroItem.Event>, date: LocalDate): ErrandsDay? {
    val pending = events
        .filter { it.categoryText in ERRAND_CATEGORIES && !it.isCompleted && it.occursOn(date) }
        .sortedForDay(date)
        .filterIsInstance<SyncroItem.Event>()
    return ErrandsDay(date, pending.map { it.title }).takeIf { pending.size >= 2 }
}
