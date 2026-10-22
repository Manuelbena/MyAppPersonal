package com.syncro.domain.usecase

import com.syncro.domain.model.Habit
import com.syncro.domain.model.Recurrence
import com.syncro.domain.model.RepeatFrequency
import com.syncro.domain.model.RepeatScope
import com.syncro.domain.model.SyncroItem
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/**
 * "Sí, cada martes": el hábito pasa a repetirse cada semana ese día.
 * - Un evento: su última vez se convierte en el primero de la serie (conserva id y copia en Google),
 *   con la misma hora, categoría, aviso y subtareas; las siguientes se crean solas.
 * - Una tarea: las tareas no se convierten (no se editan así), así que empieza una serie nueva el
 *   próximo de ese día después de la última vez, para no duplicar la que ya estuviera apuntada.
 */
class RepeatHabitUseCase @Inject constructor(
    private val saveEvent: SaveEventUseCase,
    private val saveTask: SaveTaskUseCase,
    private val clock: Clock
) {
    suspend operator fun invoke(habit: Habit): Result<Unit> {
        val weekly = Recurrence(RepeatFrequency.WEEKLY, setOf(habit.weekday))
        return when (val item = habit.latest) {
            is SyncroItem.Event -> saveEvent(
                id = item.id,
                title = item.title,
                description = item.description,
                location = item.location,
                date = item.date,
                startTime = item.startTime,
                endTime = item.endTime,
                endDate = item.endDate,
                categoryText = item.categoryText,
                categoryColor = item.categoryColor,
                priority = item.priority,
                subtasks = item.subtasks.map { it.title },
                repeat = weekly,
                scope = RepeatScope.THIS,
                reminderMinutes = item.reminderMinutes
            )
            is SyncroItem.Task -> {
                val after = maxOf(item.date, LocalDate.now(clock))
                saveTask(
                    title = item.title,
                    description = item.description.orEmpty(),
                    date = after.with(TemporalAdjusters.next(habit.weekday)),
                    time = item.time,
                    categoryText = item.categoryText,
                    categoryColor = item.categoryColor,
                    repeat = weekly,
                    reminderMinutes = item.reminderMinutes
                )
            }
            is SyncroItem.Note -> Result.success(Unit)
        }
    }
}
