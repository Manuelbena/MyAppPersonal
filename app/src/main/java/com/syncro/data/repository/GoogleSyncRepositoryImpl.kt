package com.syncro.data.repository

import android.util.Log
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.util.Data
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.api.services.tasks.model.Task
import com.syncro.data.local.dao.EventDao
import com.syncro.data.local.dao.TaskDao
import com.syncro.data.local.entity.EventEntity
import com.syncro.data.local.entity.SubtaskEntity
import com.syncro.data.local.entity.TaskEntity
import com.syncro.data.remote.GoogleRemoteDataSource
import com.syncro.data.sync.SyncScheduler
import com.syncro.domain.model.isValidEventRange
import com.syncro.domain.repository.GoogleSyncRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

class GoogleSyncRepositoryImpl @Inject constructor(
    private val remote: GoogleRemoteDataSource,
    private val taskDao: TaskDao,
    private val eventDao: EventDao,
    private val syncScheduler: SyncScheduler,
    // Fuente de "ahora" y de la zona horaria: en los tests es un reloj fijo
    private val clock: Clock,
) : GoogleSyncRepository {

    private val zone: ZoneId get() = clock.zone

    // Home y Calendario piden la sync de tareas a la vez y en cada cambio de día/mes, pero siempre
    // descarga lo mismo: se serializa y se omite si la última terminó hace menos de TASKS_SYNC_MIN_INTERVAL_MS
    private val tasksSyncMutex = Mutex()
    private var lastTasksSyncAt = 0L

    // region Sincronización Google -> local

    override suspend fun syncTasks(force: Boolean): Result<Unit> = tasksSyncMutex.withLock {
        if (!force && clock.millis() - lastTasksSyncAt < TASKS_SYNC_MIN_INTERVAL_MS) {
            return@withLock Result.success(Unit)
        }
        runGoogleCall("syncTasks") { downloadAllTasks() }
            .onSuccess { lastTasksSyncAt = clock.millis() }
    }

    private suspend fun downloadAllTasks() {
        // Foto local tomada antes de descargar: una tarea subida durante la sync no se borra
        val localRemoteIds = taskDao.getSyncedRemoteIds()
        val remoteIds = mutableSetOf<String>()

        for (taskListId in remote.listTaskListIds()) {
            remote.listTasks(taskListId).forEach {
                remoteIds += it.id
                saveRemoteTask(it, taskListId)
            }
        }

        deleteStale(localRemoteIds, remoteIds, taskDao::deleteByRemoteIds)
    }

    override suspend fun syncCalendar(
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<Unit> = runGoogleCall("syncCalendar") {
        require(!endDate.isBefore(startDate)) { "endDate ($endDate) is before startDate ($startDate)" }
        // Solo se comparan para borrado los eventos locales que empiezan dentro del rango
        val localRemoteIds = eventDao.getSyncedRemoteIdsInRange(startDate.toEpochDay(), endDate.toEpochDay())
        val timeMin = DateTime(startDate.atStartOfDay(zone).toInstant().toEpochMilli())
        val timeMax = DateTime(endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        val remoteIds = mutableSetOf<String>()

        remote.listEvents(timeMin, timeMax).forEach {
            remoteIds += it.id
            saveRemoteEvent(it)
        }

        deleteStale(localRemoteIds, remoteIds, eventDao::deleteByRemoteIds)
    }

    /**
     * Borra en local lo que tenía remoteId pero ya no existe en Google. Solo se llega aquí si la
     * descarga completa terminó sin errores, así que un fallo de red nunca borra datos locales.
     */
    private suspend fun deleteStale(
        localRemoteIds: List<String>,
        remoteIds: Set<String>,
        delete: suspend (List<String>) -> Unit
    ) {
        val stale = localRemoteIds.filterNot { it in remoteIds }
        if (stale.isEmpty()) return
        Log.d(TAG, "Deleting ${stale.size} items removed in Google")
        // Trozos para no superar el límite de variables de SQLite
        stale.chunked(SQLITE_MAX_VARIABLES).forEach { delete(it) }
    }

    private suspend fun saveRemoteTask(googleTask: Task, taskListId: String) {
        val existing = taskDao.getTaskByRemoteId(googleTask.id)
        // Cambios locales sin subir: gana la versión local, que se subirá en el próximo push
        if (existing != null && existing.pendingChanges > 0) return
        val title = googleTask.title.orEmpty()
        val description = googleTask.notes.orEmpty()
        val dateEpoch = resolveTaskDate(googleTask).toEpochDay()
        // Google Tasks no guarda la hora: se respeta la local y, si es nueva, es de todo el día (00:00)
        val time = existing?.time ?: ALL_DAY_TIME
        val isCompleted = googleTask.status == TASK_STATUS_COMPLETED

        val entity = existing?.copy(
            title = title,
            description = description,
            date = dateEpoch,
            time = time,
            isCompleted = isCompleted,
            taskListId = taskListId
        ) ?: TaskEntity(
            id = UUID.randomUUID().toString(),
            remoteId = googleTask.id,
            taskListId = taskListId,
            title = title,
            description = description,
            date = dateEpoch,
            time = time,
            isCompleted = isCompleted,
            categoryText = CATEGORY_GENERAL,
            categoryColor = COLOR_SLATE_400
        )
        taskDao.insertTask(entity)
    }

    private suspend fun saveRemoteEvent(googleEvent: Event) {
        val title = googleEvent.summary.orEmpty()
        val existing = eventDao.getEventByRemoteId(googleEvent.id)
        // Cambios locales sin subir: gana la versión local, que se subirá en el próximo push
        if (existing != null && existing.pendingChanges > 0) return
        // Reutilizar el id local si ya existe para no duplicar el evento si cambió el título
        val eventId = existing?.id ?: UUID.randomUUID().toString()
        val (description, subtasks) = parseDescription(googleEvent.description.orEmpty(), eventId)
        val (categoryName, categoryColor) = resolveCategory(title, googleEvent.colorId)
        val isAllDay = googleEvent.start.dateTime == null
        val startDate = googleEvent.start.toLocalDate()
        // En los de día completo Google da el fin como exclusivo (el día siguiente al último)
        val endDate = if (isAllDay) maxOf(startDate, googleEvent.end.toLocalDate().minusDays(1)) else googleEvent.end.toLocalDate()

        val entity = EventEntity(
            id = eventId,
            remoteId = googleEvent.id,
            title = cleanTitle(title),
            description = description,
            date = startDate.toEpochDay(),
            endDate = endDate.toEpochDay(),
            startTime = formatTime(googleEvent.start.dateTime),
            endTime = formatTime(googleEvent.end.dateTime),
            categoryText = categoryName,
            categoryColor = categoryColor,
            priority = "MEDIUM",
            isAllDay = isAllDay,
            location = googleEvent.location,
            isCompleted = isCompletedTitle(title),
            // Google no sabe de la serie (solo le llegan repeticiones sueltas): se conserva la local
            seriesId = existing?.seriesId
        )
        eventDao.insertEventWithSubtasks(entity, subtasks)
    }

    // endregion

    // region Subida local -> Google

    // Serializa las subidas: la inmediata de un caso de uso y la del worker podrían crear el
    // mismo elemento dos veces en Google
    private val pushMutex = Mutex()

    override suspend fun pushTask(taskId: String): Result<Unit> =
        pushMutex.withLock { runGoogleCall("pushTask") { pushTaskInternal(taskId) } }
            .onFailure { syncScheduler.schedulePendingPush() }

    override suspend fun pushEvent(eventId: String): Result<Unit> =
        pushMutex.withLock { runGoogleCall("pushEvent") { pushEventInternal(eventId) } }
            .onFailure { syncScheduler.schedulePendingPush() }

    override suspend fun pushPendingChanges(): Result<Unit> =
        pushMutex.withLock {
            runGoogleCall("pushPendingChanges") {
                // Se intenta subir todo aunque falle algún elemento; al final se propaga el primer error
                var firstError: Exception? = null
                suspend fun attempt(push: suspend () -> Unit) {
                    try {
                        push()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Pending item upload failed", e)
                        if (firstError == null) firstError = e
                    }
                }
                taskDao.getPendingTaskIds().forEach { attempt { pushTaskInternal(it) } }
                eventDao.getPendingEventIds().forEach { attempt { pushEventInternal(it) } }
                firstError?.let { throw it }
            }
        }.onFailure { syncScheduler.schedulePendingPush() }

    override fun observePendingChangesCount(): Flow<Int> =
        combine(taskDao.observePendingCount(), eventDao.observePendingCount()) { tasks, events -> tasks + events }

    private suspend fun pushTaskInternal(taskId: String) {
        val task = taskDao.getTaskById(taskId) ?: return
        if (task.remoteId != null && task.pendingChanges == 0) return
        if (task.isDeleted) {
            // Borrada en la app: se borra en Google (si llegó a subirse) y después del todo en local.
            // Si en Google ya no existía (404/410), el resultado es el mismo
            task.remoteId?.let { remoteId ->
                try {
                    remote.deleteTask(task.taskListId ?: DEFAULT_TASK_LIST, remoteId)
                } catch (e: GoogleJsonResponseException) {
                    if (!e.isNotFound()) throw e
                }
            }
            taskDao.deleteTaskById(task.id)
            return
        }

        val body = Task().apply {
            setTitle(task.title)
            setNotes(task.description)
            setStatus(if (task.isCompleted) TASK_STATUS_COMPLETED else TASK_STATUS_NEEDS_ACTION)
            // En un patch, null significa "no tocar"; NULL_STRING borra la fecha de completado
            setCompleted(
                if (task.isCompleted) DateTime(clock.millis()).toStringRfc3339() else Data.NULL_STRING
            )
        }
        val remoteId = task.remoteId
        // La fecha se envía al crear y cuando se cambió en la app (pasar a mañana…); si no, un patch
        // añadiría fecha a las tareas de Google que no la tienen.
        // Google Tasks solo guarda la fecha de vencimiento; la hora se descarta
        if (remoteId == null || task.dateChanged) {
            body.setDue("${LocalDate.ofEpochDay(task.date)}T00:00:00.000Z")
        }
        val syncedRemoteId = if (remoteId == null) {
            remote.insertTask(DEFAULT_TASK_LIST, body).id
        } else {
            try {
                // Las tareas sin lista conocida (creadas desde la app o previas a la migración) están en @default
                remote.patchTask(task.taskListId ?: DEFAULT_TASK_LIST, remoteId, body)
                remoteId
            } catch (e: GoogleJsonResponseException) {
                if (!e.isNotFound()) throw e
                // Se borró en Google mientras había cambios locales: gana el borrado
                taskDao.deleteTaskById(task.id)
                return
            }
        }
        taskDao.markSynced(task.id, syncedRemoteId, task.pendingChanges)
    }

    private suspend fun pushEventInternal(eventId: String) {
        val local = eventDao.getEventById(eventId) ?: return
        if (local.remoteId != null && local.pendingChanges == 0) return
        if (local.isDeleted) {
            // Borrado en la app: se borra en Google (si llegó a subirse) y después del todo en local.
            // Si en Google ya no existía (404/410), el resultado es el mismo
            local.remoteId?.let { remoteId ->
                try {
                    remote.deleteEvent(remoteId)
                } catch (e: GoogleJsonResponseException) {
                    if (!e.isNotFound()) throw e
                }
            }
            eventDao.deleteEventById(local.id)
            return
        }
        val start = LocalDate.ofEpochDay(local.date).atTime(LocalTime.parse(local.startTime))
        val end = LocalDate.ofEpochDay(local.endDate).atTime(LocalTime.parse(local.endTime))
        if (!isValidEventRange(start, end)) {
            // Google lo rechazaría siempre (400): se queda pendiente hasta que el usuario lo corrija
            Log.w(TAG, "Skipping event with end before start: '${local.title}' on ${LocalDate.ofEpochDay(local.date)}")
            return
        }

        val body = local.toGoogleEvent(eventDao.getSubtasksForEvent(eventId))
        val remoteId = local.remoteId
        val syncedRemoteId = if (remoteId == null) {
            remote.insertEvent(body).id
        } else {
            try {
                remote.patchEvent(remoteId, body)
                remoteId
            } catch (e: GoogleJsonResponseException) {
                if (!e.isNotFound()) throw e
                // Se borró en Google mientras había cambios locales: gana el borrado
                eventDao.deleteEventById(local.id)
                return
            }
        }
        eventDao.markSynced(local.id, syncedRemoteId, local.pendingChanges)
    }

    private fun EventEntity.toGoogleEvent(subtasks: List<SubtaskEntity>): Event {
        val eventDate = LocalDate.ofEpochDay(date)
        val eventEndDate = LocalDate.ofEpochDay(endDate)
        val googleTitle = if (isCompleted) "$COMPLETED_MARK $title" else title
        val googleDescription = buildDescription(description, subtasks)
        val googleLocation = location
        val googleColorId = CATEGORY_TO_GOOGLE_COLOR[categoryText]
        val allDay = isAllDayRange(startTime, endTime)
        val start = startTime
        val end = endTime
        return Event().apply {
            summary = googleTitle
            description = googleDescription
            location = googleLocation
            colorId = googleColorId
            if (allDay) {
                // En Google el fin de un evento de día completo es exclusivo: el día siguiente
                setStart(EventDateTime().setDate(DateTime(eventDate.toString())))
                setEnd(EventDateTime().setDate(DateTime(eventEndDate.plusDays(1).toString())))
            } else {
                setStart(eventDateTime(eventDate, start))
                setEnd(eventDateTime(eventEndDate, end))
            }
        }
    }

    private fun GoogleJsonResponseException.isNotFound() = statusCode == 404 || statusCode == 410

    // endregion

    // region Infraestructura

    /**
     * Ejecuta [block] en IO y lo envuelve en un [Result]. Los errores (incluido
     * `UserRecoverableAuthIOException`, que la UI usa para pedir permisos) se registran y se
     * devuelven como fallo; la cancelación de la corrutina se propaga.
     */
    private suspend fun runGoogleCall(operation: String, block: suspend () -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                block()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "$operation failed", e)
                Result.failure(e)
            }
        }

    // endregion

    // region Mapeo y parseo

    private fun cleanTitle(title: String): String = title
        .replace("[x]", "", ignoreCase = true)
        .replace("[ ]", "")
        .replace(COMPLETED_MARK, "")
        .trim()

    private fun isCompletedTitle(title: String): Boolean =
        title.contains("[x]", ignoreCase = true) ||
            title.contains(COMPLETED_MARK) ||
            title.startsWith("Cancelado", ignoreCase = true) ||
            title.startsWith("Done", ignoreCase = true)

    /** Categoría y color de la app a partir del título o del colorId de Google Calendar. */
    private fun resolveCategory(title: String, colorId: String?): Pair<String, Int> {
        fun matches(category: String, vararg keywords: String) =
            colorId == CATEGORY_TO_GOOGLE_COLOR[category] ||
                keywords.any { title.contains(it, ignoreCase = true) }

        return when {
            matches("Trabajo", "Trabajo") -> "Trabajo" to 0xFF6366F1.toInt() // Indigo500
            matches("Salud", "Salud") -> "Salud" to 0xFFFF5252.toInt()
            matches("Ocio", "Ocio") -> "Ocio" to 0xFFF59E0B.toInt() // Amber500
            matches("Personal", "Personal", "Cita", "Médico") -> "Personal" to 0xFF10B981.toInt() // Emerald500
            matches("Deporte", "Deporte", "Gimnasio") -> "Deporte" to 0xFFEC4899.toInt() // Pink500
            matches("Compras", "Compras") -> "Compras" to 0xFF8B5CF6.toInt() // Violet500
            matches("Recados", "Recados") -> "Recados" to 0xFF0EA5E9.toInt() // Sky500
            // "Otro" solo por color: la palabra es demasiado común para buscarla en el título
            colorId == CATEGORY_TO_GOOGLE_COLOR["Otro"] -> "Otro" to 0xFF64748B.toInt() // Slate500
            else -> CATEGORY_GENERAL to (GOOGLE_COLORS[colorId] ?: GOOGLE_DEFAULT_COLOR)
        }
    }

    /**
     * Separa la descripción real de la lista de subtareas que Syncro añade tras "Subtareas:".
     * Formato de línea aceptado: `- [ ] título`, `- [x] título`, `x título` o texto libre.
     */
    private fun parseDescription(raw: String, eventId: String): Pair<String, List<SubtaskEntity>> {
        val parts = raw.split(SUBTASKS_HEADER_REGEX, limit = 2)
        if (parts.size < 2) return raw to emptyList()

        val subtasks = parts[1].lines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.contains("vacio", ignoreCase = true)) return@mapNotNull null
            val match = SUBTASK_LINE_REGEX.matchEntire(trimmed) ?: return@mapNotNull null
            val subtaskTitle = match.groupValues[2].trim()
            if (subtaskTitle.isEmpty()) return@mapNotNull null
            SubtaskEntity(
                eventId = eventId,
                title = subtaskTitle,
                isCompleted = match.groupValues[1].contains('x', ignoreCase = true)
            )
        }
        return parts[0].trim() to subtasks
    }

    private fun buildDescription(description: String?, subtasks: List<SubtaskEntity>): String = buildString {
        description?.let { append(it).append("\n\n") }
        if (subtasks.isNotEmpty()) {
            append("Subtareas:\n")
            subtasks.forEach { append(if (it.isCompleted) "- [x] " else "- [ ] ").append(it.title).append('\n') }
        }
    }.trim()

    private fun isAllDayRange(startTime: String, endTime: String) =
        startTime == ALL_DAY_TIME && endTime == ALL_DAY_TIME

    private fun eventDateTime(date: LocalDate, time: String): EventDateTime {
        val instant = date.atTime(LocalTime.parse(time)).atZone(zone).toInstant()
        return EventDateTime().setDateTime(DateTime(instant.toEpochMilli()))
    }

    /** Fecha local del inicio de un evento; los eventos de día completo usan `date` sin zona. */
    private fun EventDateTime.toLocalDate(): LocalDate =
        dateTime?.let { Instant.ofEpochMilli(it.value).atZone(zone).toLocalDate() }
            ?: LocalDate.parse(date.toStringRfc3339())

    private fun formatTime(dateTime: DateTime?): String =
        dateTime?.let { Instant.ofEpochMilli(it.value).atZone(zone).format(TIME_FORMATTER) } ?: "00:00"

    /**
     * Día en que se muestra una tarea de Google Tasks:
     * - Con fecha límite: esa fecha (`due` es RFC3339 con la fecha en UTC; no se convierte de zona).
     * - Sin fecha y completada: el día en que se completó, para que no se muevan.
     * - Sin fecha y pendiente: hoy, para que no queden olvidadas en días pasados.
     */
    private fun resolveTaskDate(googleTask: Task): LocalDate {
        val today = LocalDate.now(clock)
        googleTask.due?.let { due ->
            return runCatching { OffsetDateTime.parse(due).toLocalDate() }.getOrDefault(today)
        }
        val completed = googleTask.completed
        if (googleTask.status == TASK_STATUS_COMPLETED && completed != null) {
            return runCatching { OffsetDateTime.parse(completed).atZoneSameInstant(zone).toLocalDate() }
                .getOrDefault(today)
        }
        return today
    }

    // endregion

    private companion object {
        const val TAG = "GoogleSync"
        const val DEFAULT_TASK_LIST = "@default"
        const val TASK_STATUS_COMPLETED = "completed"
        const val TASK_STATUS_NEEDS_ACTION = "needsAction"
        const val TASKS_SYNC_MIN_INTERVAL_MS = 60_000L
        const val ALL_DAY_TIME = "00:00" // Los eventos de día completo se guardan como 00:00–00:00
        const val SQLITE_MAX_VARIABLES = 500
        const val COMPLETED_MARK = "✅"
        const val CATEGORY_GENERAL = "General"
        val COLOR_SLATE_400 = 0xFF94A3B8.toInt()
        val GOOGLE_DEFAULT_COLOR = 0xFF4285F4.toInt()

        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val SUBTASKS_HEADER_REGEX = Regex("subtareas:", RegexOption.IGNORE_CASE)
        // Grupo 1: marca de estado opcional ([ ], [x] o "x "); grupo 2: título
        val SUBTASK_LINE_REGEX = Regex("""^[-•*]?\s*(\[[ xX]]|[xX](?=\s))?\s*(.*)$""")

        // Categoría de la app -> colorId de Google Calendar
        val CATEGORY_TO_GOOGLE_COLOR = mapOf(
            "Trabajo" to "6",   // Mandarina
            "Personal" to "2",  // Salvia
            "Salud" to "11",    // Tomate
            "Ocio" to "5",      // Plátano
            "Deporte" to "4",   // Flamenco
            "Compras" to "3",   // Uva
            "Recados" to "7",   // Pavo real
            "Otro" to "8"       // Grafito
        )

        val GOOGLE_COLORS = mapOf(
            "1" to 0xFFA4BDFC.toInt(),
            "2" to 0xFF7AE7BF.toInt(),
            "3" to 0xFFBDADFF.toInt(),
            "4" to 0xFFFF887C.toInt(),
            "5" to 0xFFFBD75B.toInt(),
            "6" to 0xFFFFB878.toInt(),
            "7" to 0xFF46D6DB.toInt(),
            "8" to 0xFFE1E1E1.toInt(),
            "9" to 0xFF5484ED.toInt(),
            "10" to 0xFF51B749.toInt(),
            "11" to 0xFFDC2127.toInt()
        )
    }
}
