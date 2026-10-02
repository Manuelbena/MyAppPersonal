package com.syncro.testutil

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException
import com.google.api.client.util.Data
import com.google.api.client.util.DateTime
import com.google.api.client.util.GenericData
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.api.services.tasks.model.Task
import com.syncro.data.remote.GoogleRemoteDataSource
import com.syncro.data.sync.SyncScheduler
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Google Tasks + Calendar en memoria. Reproduce las partes de la API real de las que depende la
 * sincronización:
 *  - "@default" es un alias de la lista principal,
 *  - un patch solo cambia los campos enviados (y Data.NULL_STRING borra el campo),
 *  - modificar algo que no existe devuelve 404,
 *  - [networkError] simula que no hay conexión en cualquier llamada.
 */
class FakeGoogleRemoteDataSource : GoogleRemoteDataSource {

    val taskLists = linkedMapOf<String, LinkedHashMap<String, Task>>(DEFAULT_LIST_ID to linkedMapOf())
    val events = linkedMapOf<String, Event>()
    val calls = mutableListOf<String>()

    /** Si no es null, todas las llamadas fallan con este error (sin conexión). */
    var networkError: IOException? = null

    /** Títulos que Google rechaza al crear o actualizar (para probar fallos parciales). */
    val failingTitles = mutableSetOf<String>()

    private var nextId = 1

    // region Preparar el estado de "Google" en los tests

    fun addTask(task: Task, taskListId: String = DEFAULT_LIST_ID) {
        taskLists.getOrPut(taskListId) { linkedMapOf() }[task.id] = task
    }

    fun addEvent(event: Event) {
        events[event.id] = event
    }

    fun task(remoteId: String): Task? = taskLists.values.firstNotNullOfOrNull { it[remoteId] }

    // endregion

    override suspend fun listTaskListIds(): List<String> {
        record("listTaskLists")
        return taskLists.keys.toList()
    }

    override suspend fun listTasks(taskListId: String): List<Task> {
        record("listTasks($taskListId)")
        return taskLists[resolve(taskListId)]?.values?.map { it.clone() }.orEmpty()
    }

    override suspend fun insertTask(taskListId: String, task: Task): Task {
        record("insertTask($taskListId)")
        failIfRejected(task.title)
        val created = task.clone().setId("g-task-${nextId++}")
        taskLists.getOrPut(resolve(taskListId)) { linkedMapOf() }[created.id] = created
        return created.clone()
    }

    override suspend fun patchTask(taskListId: String, taskId: String, task: Task) {
        record("patchTask($taskListId, $taskId)")
        failIfRejected(task.title)
        val existing = taskLists[resolve(taskListId)]?.get(taskId) ?: throw notFound()
        existing.applyPatch(task)
    }

    override suspend fun deleteTask(taskListId: String, taskId: String) {
        record("deleteTask($taskListId, $taskId)")
        taskLists[resolve(taskListId)]?.remove(taskId) ?: throw notFound()
    }

    override suspend fun listEvents(timeMin: DateTime, timeMax: DateTime): List<Event> {
        record("listEvents")
        return events.values
            .filter { it.start.millis() < timeMax.value && it.end.millis() > timeMin.value }
            .map { it.clone() }
    }

    override suspend fun insertEvent(event: Event): Event {
        record("insertEvent")
        failIfRejected(event.summary)
        val created = event.clone().setId("g-event-${nextId++}")
        events[created.id] = created
        return created.clone()
    }

    override suspend fun patchEvent(eventId: String, event: Event) {
        record("patchEvent($eventId)")
        failIfRejected(event.summary)
        val existing = events[eventId] ?: throw notFound()
        existing.applyPatch(event)
    }

    override suspend fun deleteEvent(eventId: String) {
        record("deleteEvent($eventId)")
        events.remove(eventId) ?: throw notFound()
    }

    private fun record(call: String) {
        networkError?.let { throw it }
        calls += call
    }

    private fun failIfRejected(title: String?) {
        if (title != null && title in failingTitles) throw IOException("Google rechazó '$title'")
    }

    private fun resolve(taskListId: String) = if (taskListId == "@default") DEFAULT_LIST_ID else taskListId

    /** Semántica de PATCH: solo cambia los campos presentes; NULL_STRING los borra. */
    private fun GenericData.applyPatch(patch: GenericData) {
        patch.forEach { (key, value) -> set(key, if (Data.isNull(value)) null else value) }
    }

    private fun EventDateTime.millis(): Long = (dateTime ?: date).value

    companion object {
        const val DEFAULT_LIST_ID = "lista-principal"

        fun notFound() = GoogleJsonResponseException(HttpResponseException.Builder(404, "Not Found", HttpHeaders()), null)

        /** Tarea tal como la devuelve Google. [due] y [completedAt] son opcionales, como en la API. */
        fun googleTask(
            id: String,
            title: String,
            due: LocalDate? = null,
            completedAt: String? = null,
            notes: String? = null
        ): Task = Task()
            .setId(id)
            .setTitle(title)
            .setNotes(notes)
            .setStatus(if (completedAt != null) "completed" else "needsAction")
            .setCompleted(completedAt)
            .setDue(due?.let { "${it}T00:00:00.000Z" })

        /** Evento con hora (en UTC, la zona del reloj de los tests). */
        fun timedEvent(
            id: String,
            title: String,
            date: LocalDate,
            start: LocalTime,
            end: LocalTime,
            endDate: LocalDate = date,
            description: String? = null,
            colorId: String? = null,
            location: String? = null
        ): Event = Event()
            .setId(id)
            .setSummary(title)
            .setDescription(description)
            .setColorId(colorId)
            .setLocation(location)
            .setStart(EventDateTime().setDateTime(DateTime(date.atTime(start).toInstant(ZoneOffset.UTC).toEpochMilli())))
            .setEnd(EventDateTime().setDateTime(DateTime(endDate.atTime(end).toInstant(ZoneOffset.UTC).toEpochMilli())))

        /** Evento de día completo de [date] a [lastDay]: en Google el fin es exclusivo (el día siguiente). */
        fun allDayEvent(id: String, title: String, date: LocalDate, lastDay: LocalDate = date): Event = Event()
            .setId(id)
            .setSummary(title)
            .setStart(EventDateTime().setDate(DateTime(date.toString())))
            .setEnd(EventDateTime().setDate(DateTime(lastDay.plusDays(1).toString())))
    }
}

class FakeSyncScheduler : SyncScheduler {
    var scheduledPushes = 0

    override fun schedulePendingPush() {
        scheduledPushes++
    }
}
