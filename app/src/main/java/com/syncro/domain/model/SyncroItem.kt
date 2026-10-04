package com.syncro.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Prioridad de un evento. Su texto y color en pantalla se definen en la capa de presentación. */
enum class Priority { HIGH, MEDIUM, LOW }

data class Subtask(
    val title: String, 
    val isCompleted: Boolean
)

sealed class SyncroItem {
    data class Event(
        val id: String,
        val remoteId: String? = null,
        val title: String,
        val description: String?,
        val date: LocalDate,
        /** Día en que termina: el mismo que [date] salvo en eventos que cruzan la medianoche. */
        val endDate: LocalDate = date,
        val startTime: LocalTime,
        val endTime: LocalTime,
        val categoryText: String,
        val categoryColor: ArgbColor,
        val priority: Priority? = null,
        val subtasks: List<Subtask> = emptyList(),
        val isCompleted: Boolean = false,
        val location: String? = null,
        /** La serie de la que es una repetición (ver `RepeatSeries`); null si no se repite. */
        val seriesId: String? = null,
        /** Cómo se repite su serie, para mostrarlo; null si no se repite. */
        val repeat: Recurrence? = null,
        /** Aviso: cuántos minutos antes de empezar (negativo = después, p. ej. "ese día a las 9:00" en uno de todo el día); null = sin aviso. */
        val reminderMinutes: Int? = null
    ) : SyncroItem() {
        /**
         * Los eventos de día completo se guardan como 00:00–00:00 (así llegan también desde
         * Google); si duran varios días, van de [date] a [endDate], ambos incluidos.
         */
        val isAllDay: Boolean get() = startTime == LocalTime.MIDNIGHT && endTime == LocalTime.MIDNIGHT

        /** Si el evento ocupa parte de [day]: así un evento de 21:30 a 01:00 aparece en los dos días. */
        fun occursOn(day: LocalDate): Boolean = when {
            day.isBefore(date) || day.isAfter(endDate) -> false
            isAllDay -> true
            // Si termina justo a las 00:00 no ocupa nada de ese último día
            day == endDate && day != date && endTime == LocalTime.MIDNIGHT -> false
            else -> true
        }

        /** Días en los que se muestra el evento. */
        val days: List<LocalDate>
            get() = generateSequence(date) { it.plusDays(1) }
                .takeWhile { !it.isAfter(endDate) }
                .filter { occursOn(it) }
                .toList()
    }

    data class Task(
        val id: String,
        val remoteId: String? = null,
        val title: String,
        val description: String? = null,
        val date: LocalDate,
        val time: LocalTime,
        val isCompleted: Boolean,
        val categoryText: String? = null,
        val categoryColor: ArgbColor? = null,
        /** La serie de la que es una repetición (ver `RepeatSeries`); null si no se repite. */
        val seriesId: String? = null,
        /** Cómo se repite su serie, para mostrarlo; null si no se repite. */
        val repeat: Recurrence? = null,
        /** Aviso: cuántos minutos antes de empezar (negativo = después, p. ej. "ese día a las 9:00" en uno de todo el día); null = sin aviso. */
        val reminderMinutes: Int? = null
    ) : SyncroItem() {
        /**
         * Tarea sin hora concreta. Se guarda a las 00:00 (misma convención que los eventos de día
         * completo): así llegan las de Google Tasks, que solo guarda la fecha.
         */
        val isAllDay: Boolean get() = time == LocalTime.MIDNIGHT
    }

    data class Note(
        val id: String,
        val title: String,
        val content: String,
        val color: ArgbColor,
        val createdAt: LocalDateTime
    ) : SyncroItem()
}
