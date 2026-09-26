package com.syncro.domain.model

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
        val date: java.time.LocalDate,
        val startTime: String, 
        val endTime: String,
        val categoryText: String,
        val categoryColor: ArgbColor,
        val priority: Priority? = null,
        val subtasks: List<Subtask> = emptyList(),
        val isCompleted: Boolean = false,
        val location: String? = null
    ) : SyncroItem()

    data class Task(
        val id: String,
        val remoteId: String? = null,
        val title: String,
        val description: String? = null,
        val date: java.time.LocalDate,
        val time: String,
        val isCompleted: Boolean,
        val categoryText: String? = null,
        val categoryColor: ArgbColor? = null
    ) : SyncroItem()

    data class Note(
        val id: String,
        val title: String,
        val content: String,
        val color: ArgbColor,
        val createdAt: java.time.LocalDateTime
    ) : SyncroItem()
}
