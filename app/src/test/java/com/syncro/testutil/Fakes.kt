package com.syncro.testutil

import com.syncro.domain.model.SyncroItem
import com.syncro.domain.repository.EventRepository
import com.syncro.domain.repository.GoogleSyncRepository
import com.syncro.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.time.LocalDate

/*
 * Dobles de prueba para los casos de uso.
 *
 * Son "fakes": implementaciones reales pero en memoria de las interfaces del dominio. Frente a
 * un mock (que solo responde lo que le programas), un fake se comporta como el de verdad, así
 * que los tests comprueban el resultado final y no cómo se llamó a cada método por dentro.
 *
 * Todos anotan sus llamadas en un [CallLog] compartido para poder verificar el orden entre
 * repositorios, por ejemplo "primero se guarda en local y después se sube a Google".
 */

class CallLog {
    val calls = mutableListOf<String>()
}

class FakeTaskRepository(private val log: CallLog = CallLog()) : TaskRepository {
    val tasks = MutableStateFlow<Map<String, SyncroItem.Task>>(emptyMap())

    override fun getTasksByDate(date: LocalDate): Flow<List<SyncroItem.Task>> =
        tasks.map { all -> all.values.filter { it.date == date } }

    override fun getTasksInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<SyncroItem.Task>> =
        tasks.map { all -> all.values.filter { it.date in startDate..endDate } }

    override suspend fun insertTask(task: SyncroItem.Task) {
        log.calls += "insertTask(${task.id})"
        tasks.update { it + (task.id to task) }
    }

    override suspend fun getTaskById(taskId: String): SyncroItem.Task? = tasks.value[taskId]

    override suspend fun toggleTaskCompletion(taskId: String) {
        log.calls += "toggleTask($taskId)"
        tasks.update { all ->
            val task = all[taskId] ?: return@update all
            all + (taskId to task.copy(isCompleted = !task.isCompleted))
        }
    }
}

class FakeEventRepository(private val log: CallLog = CallLog()) : EventRepository {
    val events = MutableStateFlow<Map<String, SyncroItem.Event>>(emptyMap())

    override fun getEventsByDate(date: LocalDate): Flow<List<SyncroItem.Event>> =
        events.map { all -> all.values.filter { it.date == date } }

    override fun getEventsInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<SyncroItem.Event>> =
        events.map { all -> all.values.filter { it.date in startDate..endDate } }

    override suspend fun insertEvent(event: SyncroItem.Event) {
        log.calls += "insertEvent(${event.id})"
        events.update { it + (event.id to event) }
    }

    override suspend fun getEventById(eventId: String): SyncroItem.Event? = events.value[eventId]

    override suspend fun toggleSubtaskCompletion(eventId: String, subtaskTitle: String) {
        log.calls += "toggleSubtask($eventId, $subtaskTitle)"
        events.update { all ->
            val event = all[eventId] ?: return@update all
            val subtasks = event.subtasks.map {
                if (it.title == subtaskTitle) it.copy(isCompleted = !it.isCompleted) else it
            }
            all + (eventId to event.copy(subtasks = subtasks))
        }
    }

    override suspend fun toggleEventCompletion(eventId: String) {
        log.calls += "toggleEvent($eventId)"
        events.update { all ->
            val event = all[eventId] ?: return@update all
            all + (eventId to event.copy(isCompleted = !event.isCompleted))
        }
    }
}

/** Simula Google: anota qué se sube y permite simular que no hay conexión con [isOffline]. */
class FakeGoogleSyncRepository(private val log: CallLog = CallLog()) : GoogleSyncRepository {
    var isOffline = false
    val pushedTaskIds = mutableListOf<String>()
    val pushedEventIds = mutableListOf<String>()

    private fun result(): Result<Unit> =
        if (isOffline) Result.failure(IOException("Sin conexión")) else Result.success(Unit)

    override suspend fun syncTasks(force: Boolean): Result<Unit> = result()

    override suspend fun syncCalendar(startDate: LocalDate, endDate: LocalDate): Result<Unit> = result()

    override suspend fun pushTask(taskId: String): Result<Unit> {
        log.calls += "pushTask($taskId)"
        pushedTaskIds += taskId
        return result()
    }

    override suspend fun pushEvent(eventId: String): Result<Unit> {
        log.calls += "pushEvent($eventId)"
        pushedEventIds += eventId
        return result()
    }

    override suspend fun pushPendingChanges(): Result<Unit> = result()
}
