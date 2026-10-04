package com.syncro.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Una serie de tareas o eventos que se repiten (solo local): la regla y la plantilla de cada
 * repetición. Las repeticiones son filas normales de `tasks`/`events` con este id en `seriesId`.
 */
@Entity(tableName = "repeat_series")
data class RepeatSeriesEntity(
    @PrimaryKey val id: String,
    val kind: String, // TASK o EVENT
    val frequency: String, // DAILY, WEEKLY, MONTHLY, YEARLY
    val weekdays: String, // Días de la semana (1 = lunes … 7 = domingo) separados por comas; vacío si no es semanal
    val startDate: Long, // LocalDate.toEpochDay()
    val untilDate: Long?, // Último día con repetición; null = sin fin
    val generatedUntil: Long, // Repeticiones ya creadas hasta este día
    // Plantilla: la tarea o el evento tal como es el primer día
    val title: String,
    val description: String?,
    val location: String?,
    val startTime: String, // HH:mm (la hora de la tarea)
    val endTime: String, // HH:mm (solo eventos)
    val spanDays: Int, // Días entre el inicio y el fin (eventos que cruzan la medianoche)
    val categoryText: String?,
    val categoryColor: Int?,
    val priority: String?,
    val subtasks: String, // Títulos de las subtareas, uno por línea
    val reminderMinutes: Int? // Aviso de cada repetición (minutos antes de empezar); null = sin aviso
)
