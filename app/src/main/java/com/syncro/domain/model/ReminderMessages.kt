package com.syncro.domain.model

import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Lo que dice la notificación de un aviso. */
data class ReminderMessage(val title: String, val text: String)

private val SPANISH = Locale("es", "ES")
private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

/**
 * El texto del aviso según cuánto falta, como lo diría alguien que te recuerda algo:
 * "Empieza a las 19:00 · en 15 min", "Empieza ahora", "Mañana a las 9:30", "Hoy, todo el día" o
 * "El lunes 5 de octubre a las 18:00". Con ubicación, se añade "· 📍 Gimnasio". Las tareas
 * dicen "Tarea para hoy" en vez de "todo el día".
 */
fun DueReminder.toMessage(now: LocalDateTime): ReminderMessage {
    val day = start.toLocalDate()
    val today = now.toLocalDate()
    val minutesLeft = Duration.between(now, start).toMinutes()

    val whenText = when {
        isAllDay && isTask -> when (day) {
            today -> "Tarea para hoy"
            today.plusDays(1) -> "Tarea para mañana"
            else -> "Tarea para el ${longDay(day)}"
        }
        isAllDay -> when (day) {
            today -> "Hoy, todo el día"
            today.plusDays(1) -> "Mañana, todo el día"
            else -> "El ${longDay(day)}, todo el día"
        }
        minutesLeft in -1..0 -> "Empieza ahora"
        minutesLeft < 0 -> "Empezó a las ${start.format(TIME)}"
        day == today -> "Empieza a las ${start.format(TIME)} · ${inTime(minutesLeft)}"
        day == today.plusDays(1) -> "Mañana a las ${start.format(TIME)}"
        else -> "El ${longDay(day)} a las ${start.format(TIME)}"
    }
    val text = location?.takeIf { it.isNotBlank() }?.let { "$whenText · 📍 $it" } ?: whenText
    return ReminderMessage(title = title, text = text)
}

/** "en 15 min", "en 1 h", "en 2 h 30 min". */
private fun inTime(minutes: Long): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0L -> "en $minutes min"
        rest == 0L -> "en $hours h"
        else -> "en $hours h $rest min"
    }
}

/** "lunes 5 de octubre". */
private fun longDay(date: java.time.LocalDate): String =
    "${date.dayOfWeek.getDisplayName(TextStyle.FULL, SPANISH)} ${date.dayOfMonth} de ${date.month.getDisplayName(TextStyle.FULL_STANDALONE, SPANISH)}"
