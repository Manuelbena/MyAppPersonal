package com.syncro.domain.repository

import com.syncro.domain.model.GoogleService
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface GoogleSyncRepository {
    /**
     * Sincroniza todas las tareas de todas las listas (la detección de borrados y las tareas sin
     * fecha necesitan la lista completa). Sin [force], se omite si ya se hizo hace poco.
     * Las tareas con cambios locales pendientes no se sobrescriben.
     */
    suspend fun syncTasks(force: Boolean = false): Result<Unit>

    /**
     * Sincroniza los eventos de Google Calendar entre [startDate] y [endDate], ambos incluidos.
     * Los eventos con cambios locales pendientes no se sobrescriben.
     */
    suspend fun syncCalendar(startDate: LocalDate, endDate: LocalDate = startDate): Result<Unit>

    /**
     * Sube a Google el estado local actual de la tarea (la crea o la actualiza). Si falla,
     * queda pendiente y se reintenta automáticamente cuando haya red.
     */
    suspend fun pushTask(taskId: String): Result<Unit>

    /** Igual que [pushTask] para eventos, incluidas sus subtareas. */
    suspend fun pushEvent(eventId: String): Result<Unit>

    /** Sube todos los cambios locales pendientes, de cualquier fecha. */
    suspend fun pushPendingChanges(): Result<Unit>

    /** Cuántas tareas y eventos tienen cambios sin subir a Google o nunca se subieron. */
    fun observePendingChangesCount(): Flow<Int>

    /**
     * Los servicios de Google a los que no tenemos permiso (el usuario no lo dio o lo quitó). Se
     * sabe al intentar hablar con Google: un fallo de permiso lo añade y una descarga que funciona
     * lo quita. Sin permiso todo sigue funcionando en el móvil; los cambios esperan pendientes.
     */
    fun observeMissingAccess(): Flow<Set<GoogleService>>
}
