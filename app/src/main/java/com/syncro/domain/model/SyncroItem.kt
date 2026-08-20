package com.syncro.domain.model

import androidx.compose.ui.graphics.Color

enum class Priority(val label: String, val color: Color) {
    HIGH("ALTA", Color(0xFFFF5252)),
    MEDIUM("MEDIA", Color(0xFFFFB74D)),
    LOW("BAJA", Color(0xFF81C784))
}

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
        val categoryColor: Color,
        val priority: Priority? = null,
        val subtasks: List<Subtask> = emptyList(),
        val isCompleted: Boolean = false
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
        val categoryColor: Color? = null
    ) : SyncroItem()

    data class Note(
        val id: String,
        val title: String,
        val content: String,
        val color: Color,
        val createdAt: java.time.LocalDateTime
    ) : SyncroItem()
}
