package com.syncro.domain.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/*
 * El equilibrio de la semana: cuántas horas de eventos hubo de cada categoría. El asistente lo
 * cuenta el lunes por la mañana ("34 h de Trabajo, nada de Ocio ni Deporte") para que el usuario
 * vea a qué dedica el tiempo sin tener que sumarlo.
 */

/** Las horas de la semana que acaba en [weekEnd] (domingo), por categoría, en minutos. */
data class WeeklyBalance(val weekEnd: LocalDate, val minutesByCategory: Map<String, Long>) {
    val weekStart: LocalDate get() = weekEnd.minusDays(6)
    val totalMinutes: Long get() = minutesByCategory.values.sum()
    fun minutesOf(category: String): Long = minutesByCategory[category] ?: 0
}

/** El domingo con el que acabó la última semana completa antes de [today]. */
fun lastWeekEnd(today: LocalDate): LocalDate = today.minusDays(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

/**
 * Suma la parte de cada evento con hora que cae en la semana (uno que cruza la medianoche del
 * domingo cuenta solo hasta ahí). Los de todo el día no cuentan: un festivo no son 24 h de nada.
 * Null si la semana tuvo menos de una hora de eventos: no hay nada que contar.
 */
fun weeklyBalance(events: List<SyncroItem.Event>, weekEnd: LocalDate): WeeklyBalance? {
    val from = weekEnd.minusDays(6).atStartOfDay()
    val to = weekEnd.plusDays(1).atStartOfDay()
    val minutes = events
        .filter { !it.isAllDay }
        .groupBy { it.categoryText }
        .mapValues { (_, inCategory) ->
            inCategory.sumOf { event ->
                val start = maxOf(event.date.atTime(event.startTime), from)
                val end = minOf(event.endDate.atTime(event.endTime), to)
                if (end.isAfter(start)) Duration.between(start, end).toMinutes() else 0
            }
        }
        .filterValues { it > 0 }
    return WeeklyBalance(weekEnd, minutes).takeIf { it.totalMinutes >= MIN_WEEK_MINUTES }
}

/** Horas de trabajo a partir de las que se echa en falta algo de Ocio o Deporte. */
const val BUSY_WORK_MINUTES = 20 * 60L

private const val MIN_WEEK_MINUTES = 60L
