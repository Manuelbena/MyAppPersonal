package com.syncro.data.remote

import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.Event
import com.google.api.services.tasks.model.Task

/**
 * Frontera con las APIs de Google Tasks y Calendar: solo transporte (credenciales, HTTP,
 * paginación). Toda la lógica de sincronización vive en GoogleSyncRepositoryImpl, que así se
 * puede probar con una implementación falsa en memoria.
 *
 * Las funciones son bloqueantes por debajo: se llaman desde Dispatchers.IO.
 */
interface GoogleRemoteDataSource {
    suspend fun listTaskListIds(): List<String>

    /** Todas las tareas de la lista, incluidas las completadas que Google oculta. */
    suspend fun listTasks(taskListId: String): List<Task>

    suspend fun insertTask(taskListId: String, task: Task): Task

    suspend fun patchTask(taskListId: String, taskId: String, task: Task)

    /** Eventos del calendario principal que se solapan con [timeMin, timeMax), uno por repetición. */
    suspend fun listEvents(timeMin: DateTime, timeMax: DateTime): List<Event>

    suspend fun insertEvent(event: Event): Event

    suspend fun patchEvent(eventId: String, event: Event)
}
