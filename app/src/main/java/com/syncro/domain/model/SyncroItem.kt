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
        val startTime: LocalTime,
        val endTime: LocalTime,
        val categoryText: String,
        val categoryColor: ArgbColor,
        val priority: Priority? = null,
        val subtasks: List<Subtask> = emptyList(),
        val isCompleted: Boolean = false,
        val location: String? = null
    ) : SyncroItem() {
        /** Los eventos de día completo se guardan como 00:00–00:00 (así llegan también desde Google). */
        val isAllDay: Boolean get() = startTime == LocalTime.MIDNIGHT && endTime == LocalTime.MIDNIGHT
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
        val categoryColor: ArgbColor? = null
    ) : SyncroItem()

    data class Note(
        val id: String,
        val title: String,
        val content: String,
        val color: ArgbColor,
        val createdAt: LocalDateTime
    ) : SyncroItem()
}
