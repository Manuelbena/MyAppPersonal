package com.syncro.testutil

import android.content.Context
import com.syncro.domain.model.ArgbColor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.syncro.data.local.SyncroDatabase
import com.syncro.data.local.entity.EventEntity
import com.syncro.data.local.entity.TaskEntity
import com.syncro.domain.model.Priority
import com.syncro.domain.model.Subtask
import com.syncro.domain.model.SyncroItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

/**
 * Base de datos Room real en memoria: cada test empieza de cero y prueba el SQL de verdad
 * (REPLACE, cascadas, CASE…), que es donde han aparecido los bugs de la capa de datos.
 */
fun createInMemoryDatabase(): SyncroDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), SyncroDatabase::class.java)
        .allowMainThreadQueries()
        .build()

// Fecha fija: un test que depende de LocalDate.now() puede fallar según el día en que se ejecute
val DAY: LocalDate = LocalDate.of(2026, 9, 26)

/** Hora corta para los tests: `at("09:30")`. */
fun at(hhmm: String): LocalTime = LocalTime.parse(hhmm)

// region Constructores de datos de prueba (Test Data Builders)
// Cada test solo indica los campos que le importan; el resto toma valores válidos por defecto.

fun aTask(
    id: String = UUID.randomUUID().toString(),
    title: String = "Tarea",
    description: String? = "Descripción",
    date: LocalDate = DAY,
    time: LocalTime = at("10:00"),
    isCompleted: Boolean = false,
    categoryText: String? = "Personal",
    categoryColor: ArgbColor? = ArgbColor(0xFF10B981)
) = SyncroItem.Task(
    id = id,
    title = title,
    description = description,
    date = date,
    time = time,
    isCompleted = isCompleted,
    categoryText = categoryText,
    categoryColor = categoryColor
)

fun anEvent(
    id: String = UUID.randomUUID().toString(),
    remoteId: String? = null,
    title: String = "Evento",
    description: String? = "Descripción",
    date: LocalDate = DAY,
    startTime: LocalTime = at("10:00"),
    endTime: LocalTime = at("11:00"),
    categoryText: String = "Trabajo",
    categoryColor: ArgbColor = ArgbColor(0xFF6366F1),
    priority: Priority? = Priority.MEDIUM,
    subtasks: List<Subtask> = emptyList(),
    isCompleted: Boolean = false,
    location: String? = null
) = SyncroItem.Event(
    id = id,
    remoteId = remoteId,
    title = title,
    description = description,
    date = date,
    startTime = startTime,
    endTime = endTime,
    categoryText = categoryText,
    categoryColor = categoryColor,
    priority = priority,
    subtasks = subtasks,
    isCompleted = isCompleted,
    location = location
)

fun aNote(
    id: String = "nota-1",
    title: String = "Nota",
    content: String = "Contenido",
    color: ArgbColor = ArgbColor(0xFFF59E0B),
    createdAt: LocalDateTime = LocalDateTime.of(2026, 9, 26, 10, 0)
) = SyncroItem.Note(id = id, title = title, content = content, color = color, createdAt = createdAt)

/** Tarea tal como queda tras sincronizarse con Google: con remoteId y sin cambios pendientes. */
fun aSyncedTaskEntity(
    id: String = "tarea-1",
    remoteId: String? = "google-tarea-1",
    date: LocalDate = DAY,
    isCompleted: Boolean = false,
    pendingChanges: Int = 0
) = TaskEntity(
    id = id,
    remoteId = remoteId,
    title = "Tarea sincronizada",
    description = "",
    date = date.toEpochDay(),
    time = "10:00",
    isCompleted = isCompleted,
    pendingChanges = pendingChanges
)

/** Evento tal como queda tras sincronizarse con Google: con remoteId y sin cambios pendientes. */
fun aSyncedEventEntity(
    id: String = "evento-1",
    remoteId: String? = "google-evento-1",
    date: LocalDate = DAY,
    pendingChanges: Int = 0
) = EventEntity(
    id = id,
    remoteId = remoteId,
    title = "Evento sincronizado",
    description = null,
    date = date.toEpochDay(),
    startTime = "10:00",
    endTime = "11:00",
    categoryText = "Trabajo",
    categoryColor = 0xFF6366F1.toInt(),
    priority = "MEDIUM",
    pendingChanges = pendingChanges
)

// endregion
