package com.syncro.domain.model

import java.time.Duration
import java.time.LocalDateTime

/*
 * Avisos de tareas y eventos. Los publica Syncro en el móvil (alarma exacta), no Google: a Google
 * Calendar se le quitan sus avisos para no recibir dos. Se guardan como minutos antes de empezar,
 * así sirven igual para cada repetición de una serie.
 */

/** Cuándo empieza: un evento a su hora; uno de todo el día y las tareas, a las 00:00 de su día. */
val SyncroItem.Event.startDateTime: LocalDateTime get() = date.atTime(startTime)
val SyncroItem.Task.startDateTime: LocalDateTime get() = date.atTime(time)

/** Cuándo suena el aviso, o null si no tiene. */
fun SyncroItem.reminderAt(): LocalDateTime? = when (this) {
    is SyncroItem.Event -> reminderMinutes?.let { startDateTime.minusMinutes(it.toLong()) }
    is SyncroItem.Task -> reminderMinutes?.let { startDateTime.minusMinutes(it.toLong()) }
    is SyncroItem.Note -> null
}

/** Los minutos que hay que guardar para que el aviso suene en [at] (de "Otra hora…"). */
fun reminderMinutesFor(start: LocalDateTime, at: LocalDateTime): Int = Duration.between(at, start).toMinutes().toInt()

/** Un aviso que hay que publicar: lo que se enseña en la notificación. */
data class DueReminder(
    val itemId: String,
    val isTask: Boolean,
    val title: String,
    val at: LocalDateTime,
    val start: LocalDateTime,
    val isAllDay: Boolean,
    val location: String?,
    val color: ArgbColor?
)

/** Los avisos de estas tareas y eventos, los más próximos primero. Sin los completados. */
fun dueReminders(tasks: List<SyncroItem.Task>, events: List<SyncroItem.Event>): List<DueReminder> {
    val fromTasks = tasks.filter { !it.isCompleted }.mapNotNull { task ->
        task.reminderAt()?.let { DueReminder(task.id, true, task.title, it, task.startDateTime, task.isAllDay, null, task.categoryColor) }
    }
    val fromEvents = events.filter { !it.isCompleted }.mapNotNull { event ->
        event.reminderAt()?.let { DueReminder(event.id, false, event.title, it, event.startDateTime, event.isAllDay, event.location, event.categoryColor) }
    }
    return (fromTasks + fromEvents).sortedBy { it.at }
}

/** El siguiente aviso que va a sonar después de [now], o null si no queda ninguno. */
fun List<DueReminder>.nextAfter(now: LocalDateTime): DueReminder? = filter { it.at.isAfter(now) }.minByOrNull { it.at }

/**
 * Los que tocan ahora: los que sonaban después de la última comprobación ([since]) y hasta [now].
 * Si el móvil estuvo apagado, como mucho los de la última [MISSED_REMINDER_GRACE]: un aviso de
 * hace dos días ya no sirve.
 */
fun List<DueReminder>.dueBetween(since: LocalDateTime, now: LocalDateTime): List<DueReminder> {
    val from = maxOf(since, now.minus(MISSED_REMINDER_GRACE))
    return filter { it.at.isAfter(from) && !it.at.isAfter(now) }
}

val MISSED_REMINDER_GRACE: Duration = Duration.ofHours(2)
