package com.syncro.presentation.home

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val SPANISH = Locale("es", "ES")

/** "Jueves, 8 de octubre"; con el año solo si no es el de [today] ("Lunes, 4 de enero de 2027"). */
internal fun fullDayName(date: LocalDate, today: LocalDate): String {
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, SPANISH).replaceFirstChar { it.uppercase() }
    val month = date.month.getDisplayName(TextStyle.FULL, SPANISH)
    val year = if (date.year != today.year) " de ${date.year}" else ""
    return "$weekday, ${date.dayOfMonth} de $month$year"
}

/** El título de la agenda del día que se mira: "Hoy", "Mañana", "Ayer" o la fecha completa. */
internal fun dayTitle(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Hoy"
    today.plusDays(1) -> "Mañana"
    today.minusDays(1) -> "Ayer"
    else -> fullDayName(date, today)
}
