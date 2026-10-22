package com.syncro.domain.model

import java.time.LocalDate

/*
 * Racha: días seguidos con todas las tareas hechas. Los días sin tareas no la rompen (no había nada
 * que hacer) ni la alargan, y hoy solo suma cuando ya está todo hecho: a media mañana no la rompe.
 * Pasar las pendientes a otro día (el repaso del asistente) también vale: el día queda limpio.
 */

/** Hasta dónde se mira hacia atrás; una racha más larga se muestra como este número. */
const val STREAK_LOOKBACK_DAYS = 60L

fun taskStreak(tasks: List<SyncroItem.Task>, today: LocalDate): Int {
    val byDay = tasks.groupBy { it.date }
    var streak = 0
    var day = today
    val oldest = today.minusDays(STREAK_LOOKBACK_DAYS)
    while (!day.isBefore(oldest)) {
        val dayTasks = byDay[day].orEmpty()
        val allDone = dayTasks.isNotEmpty() && dayTasks.all { it.isCompleted }
        when {
            allDone -> streak++
            // Hoy aún hay tiempo; y un día sin tareas no cuenta
            day == today || dayTasks.isEmpty() -> Unit
            else -> return streak
        }
        day = day.minusDays(1)
    }
    return streak
}
