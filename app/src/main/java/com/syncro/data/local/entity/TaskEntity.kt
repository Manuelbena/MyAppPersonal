package com.syncro.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String, // UUID local
    val remoteId: String? = null, // ID de Google Tasks
    val taskListId: String? = null, // Lista de Google Tasks; null = @default
    val title: String,
    val description: String,
    val date: Long, // LocalDate.toEpochDay()
    val time: String, // HH:mm
    val isCompleted: Boolean = false,
    val categoryText: String? = null,
    val categoryColor: Int? = null,
    // Cambios locales aún no confirmados por Google (0 = sincronizada). Contador y no booleano
    // para no perder un cambio hecho mientras se subía el anterior
    @ColumnInfo(defaultValue = "0") val pendingChanges: Int = 0,
    // La fecha se cambió en la app (p. ej. al pasar la tarea a mañana) y hay que mandarla a Google.
    // Sin esta marca el patch no envía la fecha, para no ponérsela a las tareas de Google que no tienen
    @ColumnInfo(defaultValue = "0") val dateChanged: Boolean = false,
    // Borrada en la app pero aún no en Google: no se muestra, y la subida la borrará allí. Sin esta
    // marca, la siguiente sincronización la volvería a traer
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false,
    // Serie de la que es una repetición (repeat_series.id); null si no se repite
    val seriesId: String? = null
)
