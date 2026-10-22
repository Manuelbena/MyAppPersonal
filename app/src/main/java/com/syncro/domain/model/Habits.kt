package com.syncro.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/*
 * Hábitos: algo que el usuario apunta a mano casi todas las semanas el mismo día ("Gimnasio" los
 * martes). El asistente lo detecta y propone que se repita solo, para no tener que crearlo cada vez.
 */

/** Semanas hacia atrás que se miran. */
const val HABIT_LOOKBACK_WEEKS = 6L

/** Semanas (de las miradas) en que tiene que aparecer para ser un hábito. */
const val HABIT_MIN_WEEKS = 3

/**
 * Un hábito detectado. [latest] es la última vez que se apuntó (la base de la repetición) y [lastDone]
 * la última que ya pasó, que es cuando el asistente lo propone.
 */
data class Habit(
    val title: String,
    val weekday: DayOfWeek,
    val weeks: Int,
    val latest: SyncroItem,
    val lastDone: LocalDate
) {
    /** Estable entre días: el mismo hábito es el mismo mensaje (y la misma respuesta guardada). */
    val key: String get() = habitKey(title, weekday)
}

/** Lo que el usuario contestó a la propuesta de un hábito. */
data class HabitAnswer(val key: String, val title: String, val weekday: DayOfWeek, val date: LocalDate, val accepted: Boolean)

fun habitKey(title: String, weekday: DayOfWeek): String =
    "${title.trim().lowercase().replace(Regex("\\s+"), "_")}-${weekday.name.lowercase()}"

/** "martes", "sábado". */
fun DayOfWeek.spanishName(): String = getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-ES"))

/**
 * Los hábitos entre [tasks] y [events] (de las últimas [HABIT_LOOKBACK_WEEKS] semanas hasta un
 * poco después de hoy): mismo título y mismo día de la semana en al menos [HABIT_MIN_WEEKS]
 * semanas distintas ya pasadas, sin repetición todavía. Si alguno con ese título ya se repite, no
 * se propone: el usuario ya lo resolvió. Solo los recientes (hechos en la última semana), para
 * proponerlo justo después de hacerlo.
 */
fun findHabits(tasks: List<SyncroItem.Task>, events: List<SyncroItem.Event>, today: LocalDate): List<Habit> {
    val items: List<Pair<SyncroItem, LocalDate>> = tasks.map { it to it.date } + events.map { it to it.date }
    val repeatingTitles = items.mapNotNull { (item, _) -> item.titleKey().takeIf { item.seriesIdOrNull() != null } }.toSet()
    val since = today.minusWeeks(HABIT_LOOKBACK_WEEKS)
    return items
        .filter { (item, _) -> item.seriesIdOrNull() == null && item.titleKey() !in repeatingTitles }
        .groupBy { (item, date) -> item.titleKey() to date.dayOfWeek }
        .mapNotNull { (key, group) ->
            val past = group.map { it.second }.filter { !it.isBefore(since) && !it.isAfter(today) }
            val weeks = past.map { ChronoUnit.WEEKS.between(since, it) }.distinct().size
            val lastDone = past.maxOrNull() ?: return@mapNotNull null
            if (weeks < HABIT_MIN_WEEKS || lastDone.isBefore(today.minusDays(6))) return@mapNotNull null
            val latest = group.maxBy { it.second }.first
            Habit(latest.title(), key.second, weeks, latest, lastDone)
        }
        .sortedBy { it.lastDone }
}

private fun SyncroItem.titleKey(): String = title().trim().lowercase()

private fun SyncroItem.title(): String = when (this) {
    is SyncroItem.Task -> title
    is SyncroItem.Event -> title
    is SyncroItem.Note -> title
}

private fun SyncroItem.seriesIdOrNull(): String? = when (this) {
    is SyncroItem.Task -> seriesId
    is SyncroItem.Event -> seriesId
    is SyncroItem.Note -> null
}
