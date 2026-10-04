package com.syncro.data.repository

import com.syncro.data.local.dao.RepeatSeriesDao
import com.syncro.data.local.entity.RepeatSeriesEntity
import com.syncro.data.local.toLocalTimeOrMidnight
import com.syncro.data.local.toStoredTime
import com.syncro.domain.model.ArgbColor
import com.syncro.domain.model.Priority
import com.syncro.domain.model.Recurrence
import com.syncro.domain.model.RepeatFrequency
import com.syncro.domain.model.RepeatSeries
import com.syncro.domain.model.Subtask
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.repository.RepeatSeriesRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class RepeatSeriesRepositoryImpl @Inject constructor(
    private val dao: RepeatSeriesDao
) : RepeatSeriesRepository {

    override suspend fun getAllSeries(): List<RepeatSeries> = dao.getAll().mapNotNull { it.toDomain() }

    override suspend fun getSeries(id: String): RepeatSeries? = dao.getById(id)?.toDomain()

    override suspend fun saveSeries(series: RepeatSeries) {
        require(series.id.isNotBlank()) { "La serie necesita un id" }
        dao.upsert(series.toEntity())
    }

    override suspend fun deleteSeries(id: String) = dao.delete(id)

    private fun RepeatSeries.toEntity(): RepeatSeriesEntity {
        val common = RepeatSeriesEntity(
            id = id,
            kind = KIND_TASK,
            frequency = recurrence.frequency.name,
            weekdays = recurrence.weekdays.sorted().joinToString(",") { it.value.toString() },
            startDate = start.toEpochDay(),
            untilDate = until?.toEpochDay(),
            generatedUntil = generatedUntil.toEpochDay(),
            title = "",
            description = null,
            location = null,
            startTime = LocalTime.MIDNIGHT.toStoredTime(),
            endTime = LocalTime.MIDNIGHT.toStoredTime(),
            spanDays = 0,
            categoryText = null,
            categoryColor = null,
            priority = null,
            subtasks = ""
        )
        return when (val item = template) {
            is SyncroItem.Task -> common.copy(
                title = item.title,
                description = item.description,
                startTime = item.time.toStoredTime(),
                categoryText = item.categoryText,
                categoryColor = item.categoryColor?.argb
            )
            is SyncroItem.Event -> common.copy(
                kind = KIND_EVENT,
                title = item.title,
                description = item.description,
                location = item.location,
                startTime = item.startTime.toStoredTime(),
                endTime = item.endTime.toStoredTime(),
                spanDays = ChronoUnit.DAYS.between(item.date, item.endDate).toInt(),
                categoryText = item.categoryText,
                categoryColor = item.categoryColor.argb,
                priority = item.priority?.name,
                subtasks = item.subtasks.joinToString("\n") { it.title }
            )
            is SyncroItem.Note -> error("Las notas no se repiten")
        }
    }

    /** Null si la fila no se entiende (tipo o regla desconocidos): mejor no repetir que repetir mal. */
    private fun RepeatSeriesEntity.toDomain(): RepeatSeries? {
        val recurrence = toRecurrence() ?: return null
        val start = LocalDate.ofEpochDay(startDate)
        val template = when (kind) {
            KIND_TASK -> SyncroItem.Task(
                id = id,
                title = title,
                description = description,
                date = start,
                time = startTime.toLocalTimeOrMidnight(),
                isCompleted = false,
                categoryText = categoryText,
                categoryColor = categoryColor?.let { ArgbColor(it) },
                seriesId = id,
                repeat = recurrence
            )
            KIND_EVENT -> SyncroItem.Event(
                id = id,
                title = title,
                description = description,
                date = start,
                endDate = start.plusDays(spanDays.toLong()),
                startTime = startTime.toLocalTimeOrMidnight(),
                endTime = endTime.toLocalTimeOrMidnight(),
                categoryText = categoryText.orEmpty(),
                categoryColor = ArgbColor(categoryColor ?: 0),
                priority = priority?.let { name -> Priority.entries.firstOrNull { it.name == name } },
                subtasks = subtasks.lines().filter { it.isNotBlank() }.map { Subtask(it, isCompleted = false) },
                location = location,
                seriesId = id,
                repeat = recurrence
            )
            else -> return null
        }
        return RepeatSeries(
            id = id,
            recurrence = recurrence,
            start = start,
            until = untilDate?.let { LocalDate.ofEpochDay(it) },
            generatedUntil = LocalDate.ofEpochDay(generatedUntil),
            template = template
        )
    }

    private companion object {
        const val KIND_TASK = "TASK"
        const val KIND_EVENT = "EVENT"
    }
}

/** La regla guardada en la fila, o null si no se entiende. La usan también los repositorios de tareas y eventos. */
internal fun RepeatSeriesEntity.toRecurrence(): Recurrence? {
    val frequency = RepeatFrequency.entries.firstOrNull { it.name == frequency } ?: return null
    val days = weekdays.split(',').mapNotNull { it.trim().toIntOrNull()?.takeIf { day -> day in 1..7 } }.map { DayOfWeek.of(it) }.toSet()
    if (frequency == RepeatFrequency.WEEKLY && days.isEmpty()) return null
    return Recurrence(frequency, if (frequency == RepeatFrequency.WEEKLY) days else emptySet())
}
