package com.syncro.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String, // UUID local
    val remoteId: String? = null, // ID de Google Calendar
    val title: String,
    val description: String?,
    val date: Long, // LocalDate.toEpochDay()
    // Día en que termina (toEpochDay). Distinto de [date] en eventos que cruzan la medianoche
    @ColumnInfo(defaultValue = "0") val endDate: Long,
    val startTime: String, // HH:mm
    val endTime: String, // HH:mm
    val categoryText: String,
    val categoryColor: Int,
    val priority: String?, // LOW, MEDIUM, HIGH
    val isAllDay: Boolean = false,
    val location: String? = null,
    val notificationEnabled: Boolean = true,
    val isCompleted: Boolean = false,
    // Cambios locales aún no confirmados por Google (0 = sincronizado)
    @ColumnInfo(defaultValue = "0") val pendingChanges: Int = 0,
    // Borrado en la app pero aún no en Google: no se muestra, y la subida lo borrará allí. Sin esta
    // marca, la siguiente sincronización lo volvería a traer
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false,
    // Serie de la que es una repetición (repeat_series.id); null si no se repite
    val seriesId: String? = null
)
