package com.syncro.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

/** Lo que te queda de hoy (para el widget): tareas sin hacer y eventos sin terminar, en el orden del día. */
data class TodayAgenda(val date: LocalDate, val items: List<SyncroItem>)

/**
 * De los elementos de hoy ([dayItems], ya en el orden de [sortedForDay]), los que aún quedan a
 * [now]: fuera las tareas hechas y los eventos completados o ya terminados. Las tareas cuya hora ya
 * pasó se quedan: siguen por hacer.
 */
fun todayAgenda(dayItems: List<SyncroItem>, now: LocalDateTime): TodayAgenda = TodayAgenda(
    date = now.toLocalDate(),
    items = dayItems.filter { item ->
        when (item) {
            is SyncroItem.Task -> !item.isCompleted
            is SyncroItem.Event -> !item.isCompleted && (item.isAllDay || item.endDate.atTime(item.endTime).isAfter(now))
            is SyncroItem.Note -> false
        }
    }
)
