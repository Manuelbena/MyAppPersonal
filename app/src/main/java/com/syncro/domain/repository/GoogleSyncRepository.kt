package com.syncro.domain.repository

import java.time.LocalDate

interface GoogleSyncRepository {
    /**
     * Sincroniza todas las tareas de todas las listas (la detección de borrados y las tareas sin
     * fecha necesitan la lista completa). Sin [force], se omite si ya se hizo hace poco.
     */
    suspend fun syncTasks(force: Boolean = false): Result<Unit>
    /** Sincroniza los eventos de Google Calendar entre [startDate] y [endDate], ambos incluidos. */
    suspend fun syncCalendar(startDate: LocalDate, endDate: LocalDate = startDate): Result<Unit>
    suspend fun uploadUnsyncedItems(date: LocalDate): Result<Unit>
    suspend fun uploadTaskToGoogle(taskId: String, title: String, notes: String?, date: LocalDate): Result<Unit>
    suspend fun updateTaskInGoogle(remoteId: String, title: String, notes: String?, isCompleted: Boolean): Result<Unit>
    /** Crea el evento en Google Calendar o, si ya tiene remoteId, actualiza el existente. */
    suspend fun uploadEventToGoogle(
        eventId: String,
        title: String, 
        description: String?, 
        location: String?, 
        startDate: LocalDate, 
        startTime: String, 
        endTime: String,
        category: String? = null,
        subtasks: List<String> = emptyList()
    ): Result<Unit>

    /** Refleja en Google el estado local actual del evento (completado, subtareas…). No-op si aún no está sincronizado. */
    suspend fun updateEventInGoogle(eventId: String): Result<Unit>
}
