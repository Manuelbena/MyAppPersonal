package com.syncro.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A qué día se proponen las tareas pendientes: por la noche a mañana, por la mañana a hoy. */
enum class MoveTarget(val label: String) {
    TODAY("Hoy"),
    TOMORROW("Mañana");

    fun dateFrom(today: LocalDate): LocalDate = if (this == TODAY) today else today.plusDays(1)
}

/**
 * Tareas que se quedaron sin hacer, agrupadas en un "repaso". [reviewDate] identifica el repaso:
 * el de la noche (desde las 21:00) revisa hoy, y el de la mañana revisa ayer, así que si el usuario
 * no contesta por la noche se le vuelve a preguntar por la mañana (mismo repaso, ahora "a hoy").
 */
data class LeftoverTasks(
    val reviewDate: LocalDate,
    val target: MoveTarget,
    val tasks: List<SyncroItem.Task>
)

/**
 * De las tareas sin hacer (hasta hoy, incluidas las atrasadas), las que cuentan como pendientes
 * ahora: antes de la hora de la noche [eveningFrom] (la del aviso de la noche, 21:00 si no se ha
 * cambiado), las de días anteriores (hoy aún hay tiempo); desde esa hora, también las de hoy.
 */
fun leftoverTasks(
    unfinished: List<SyncroItem.Task>,
    now: LocalDateTime,
    eveningFrom: LocalTime = DigestMoment.EVENING.defaultTime
): LeftoverTasks {
    val today = now.toLocalDate()
    val evening = !now.toLocalTime().isBefore(eveningFrom)
    return if (evening) {
        LeftoverTasks(today, MoveTarget.TOMORROW, unfinished.filter { !it.isCompleted && !it.date.isAfter(today) })
    } else {
        LeftoverTasks(today.minusDays(1), MoveTarget.TODAY, unfinished.filter { !it.isCompleted && it.date.isBefore(today) })
    }
}

/** Qué eligió el usuario para las tareas de un repaso. */
enum class LeftoverChoice { MOVE_ALL, ONE_BY_ONE, KEEP }

/**
 * Lo que el usuario decidió en un repaso y cómo va. [total] es cuántas había al preguntar; en
 * "una a una" se van sumando [moved] y [done] según las resuelve.
 */
data class LeftoverOutcome(
    val reviewDate: LocalDate,
    val choice: LeftoverChoice,
    val target: MoveTarget,
    val total: Int,
    val moved: Int = 0,
    val done: Int = 0
)

/** Una tarea tal como sale en la lista del chat: título y, debajo, hora o día si es atrasada. */
data class ChatTask(val id: String, val title: String, val detail: String?)

private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE d", Locale.forLanguageTag("es-ES"))
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

/** "17:00", "lun 26 · 17:00" si es de otro día que el repaso, o null si es de todo el día y del día. */
internal fun SyncroItem.Task.toChatTask(reviewDate: LocalDate): ChatTask {
    val day = if (date != reviewDate) date.format(DAY_FORMAT).removeSuffix(".") else null
    val time = if (isAllDay) null else time.format(TIME_FORMAT)
    return ChatTask(id, title, listOfNotNull(day, time).joinToString(" · ").ifEmpty { null })
}

internal fun tasksCount(n: Int) = if (n == 1) "1 tarea" else "$n tareas"
