package com.syncro.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String, // Generado como: date_title_time
    val remoteId: String? = null, // ID de Google Tasks
    val title: String,
    val description: String,
    val date: Long, // Epoch millis
    val time: String, // HH:mm
    val isCompleted: Boolean = false
)
