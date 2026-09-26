package com.syncro.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String, // UUID local
    val remoteId: String? = null, // ID de Google Calendar
    val title: String,
    val description: String?,
    val date: Long, // LocalDate.toEpochDay()
    val startTime: String, // HH:mm
    val endTime: String, // HH:mm
    val categoryText: String,
    val categoryColor: Int,
    val priority: String?, // LOW, MEDIUM, HIGH
    val isAllDay: Boolean = false,
    val location: String? = null,
    val notificationEnabled: Boolean = true,
    val isCompleted: Boolean = false
)
