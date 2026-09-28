package com.syncro.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Cuántas prioridades se pueden elegir al día: pocas, para que de verdad lo sean. */
const val MAX_FOCUS_TASKS = 3

/** Mínimo de tareas pendientes para que tenga sentido preguntar (con una sola no hay que elegir). */
const val MIN_FOCUS_CANDIDATES = 2

/** Una prioridad elegida; se guarda el título para que el chat la recuerde aunque cambie la tarea. */
data class FocusedTask(val id: String, val title: String)

/** Las prioridades de un día. Lista vacía = el usuario dijo "Hoy no". */
data class DailyFocus(val date: LocalDate, val tasks: List<FocusedTask>) {
    val skipped: Boolean get() = tasks.isEmpty()
}

class TooManyFocusTasksException : IllegalArgumentException("Elige como mucho $MAX_FOCUS_TASKS prioridades")

/** Tareas entre las que elegir las prioridades de [date]. */
data class FocusCandidates(val date: LocalDate, val tasks: List<SyncroItem.Task>)

/**
 * Las tareas de hoy sin hacer entre las que elegir prioridades, o null si ya no toca preguntar:
 * desde la hora de la noche [eveningFrom] (21:00 si no se ha cambiado) el día está acabando y lo
 * que queda es el repaso de pendientes.
 */
fun focusCandidates(
    todayTasks: List<SyncroItem.Task>,
    now: LocalDateTime,
    eveningFrom: LocalTime = DigestMoment.EVENING.defaultTime
): FocusCandidates? {
    if (!now.toLocalTime().isBefore(eveningFrom)) return null
    val today = now.toLocalDate()
    return FocusCandidates(today, todayTasks.filter { it.date == today && !it.isCompleted }.sortedBy { it.time })
}
