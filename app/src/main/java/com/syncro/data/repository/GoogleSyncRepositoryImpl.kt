package com.syncro.data.repository

import android.accounts.Account
import android.content.Context
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.model.Task
import com.syncro.data.local.dao.EventDao
import com.syncro.data.local.dao.TaskDao
import com.syncro.data.local.dao.UserDao
import com.syncro.data.local.entity.EventEntity
import com.syncro.data.local.entity.SubtaskEntity
import com.syncro.data.local.entity.TaskEntity
import com.syncro.domain.repository.GoogleSyncRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

class GoogleSyncRepositoryImpl @Inject constructor(
    private val context: Context,
    private val userDao: UserDao,
    private val taskDao: TaskDao,
    private val eventDao: EventDao,
) : GoogleSyncRepository {

    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val transport = NetHttpTransport()
    private val zone: ZoneId get() = ZoneId.systemDefault()

    // Home y Calendario piden la sync de tareas a la vez y en cada cambio de día/mes, pero siempre
    // descarga lo mismo: se serializa y se omite si la última terminó hace menos de TASKS_SYNC_MIN_INTERVAL_MS
    private val tasksSyncMutex = Mutex()
    private var lastTasksSyncAt = 0L

    // region Sincronización Google -> local

    override suspend fun syncTasks(force: Boolean): Result<Unit> = tasksSyncMutex.withLock {
        if (!force && System.currentTimeMillis() - lastTasksSyncAt < TASKS_SYNC_MIN_INTERVAL_MS) {
            return@withLock Result.success(Unit)
        }
        runGoogleCall("syncTasks") { downloadAllTasks() }
            .onSuccess { lastTasksSyncAt = System.currentTimeMillis() }
    }

    private suspend fun downloadAllTasks() {
        // Foto local tomada antes de descargar: una tarea subida durante la sync no se borra
        val localRemoteIds = taskDao.getAllRemoteIds()
        val service = tasksService()
        val remoteIds = mutableSetOf<String>()

        for (taskList in service.tasklists().list().execute().items.orEmpty()) {
            var pageToken: String? = null
            do {
                val response = service.tasks().list(taskList.id)
                    // Las apps de Google ocultan las tareas completadas; sin esto se darían por borradas
                    .setShowHidden(true)
                    .setMaxResults(MAX_TASKS_PER_PAGE)
                    .setPageToken(pageToken)
                    .execute()
                response.items.orEmpty().forEach {
                    remoteIds += it.id
                    saveRemoteTask(it, taskList.id)
                }
                pageToken = response.nextPageToken
            } while (pageToken != null)
        }

        deleteStale(localRemoteIds, remoteIds, taskDao::deleteByRemoteIds)
    }

    override suspend fun syncCalendar(
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<Unit> = runGoogleCall("syncCalendar") {
        require(!endDate.isBefore(startDate)) { "endDate ($endDate) is before startDate ($startDate)" }
        // Solo se comparan para borrado los eventos locales que empiezan dentro del rango
        val localRemoteIds = eventDao.getRemoteIdsInRange(startDate.toEpochDay(), endDate.toEpochDay())
        val service = calendarService()
        val timeMin = DateTime(startDate.atStartOfDay(zone).toInstant().toEpochMilli())
        val timeMax = DateTime(endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        val remoteIds = mutableSetOf<String>()

        var pageToken: String? = null
        do {
            val response = service.events().list(PRIMARY_CALENDAR)
                .setTimeMin(timeMin)
                .setTimeMax(timeMax)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .setPageToken(pageToken)
                .execute()
            response.items.orEmpty().forEach {
                remoteIds += it.id
                saveRemoteEvent(it)
            }
            pageToken = response.nextPageToken
        } while (pageToken != null)

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
        val title = googleTask.title.orEmpty()
        val description = googleTask.notes.orEmpty()
        val dateEpoch = resolveTaskDate(googleTask).toEpochDay()
        // Google Tasks no guarda la hora (due siempre llega a las 00:00): se respeta la local
        val time = existing?.time ?: if (googleTask.due != null) "00:00" else DEFAULT_TASK_TIME
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
        // Reutilizar el id local si ya existe para no duplicar el evento si cambió el título
        val eventId = eventDao.getEventByRemoteId(googleEvent.id)?.id ?: UUID.randomUUID().toString()
        val (description, subtasks) = parseDescription(googleEvent.description.orEmpty(), eventId)
        val (categoryName, categoryColor) = resolveCategory(title, googleEvent.colorId)

        val entity = EventEntity(
            id = eventId,
            remoteId = googleEvent.id,
            title = cleanTitle(title),
            description = description,
            date = googleEvent.start.toLocalDate().toEpochDay(),
            startTime = formatTime(googleEvent.start.dateTime),
            endTime = formatTime(googleEvent.end.dateTime),
            categoryText = categoryName,
            categoryColor = categoryColor,
            priority = "MEDIUM",
            isAllDay = googleEvent.start.dateTime == null,
            location = googleEvent.location,
            isCompleted = isCompletedTitle(title)
        )
        eventDao.insertEventWithSubtasks(entity, subtasks)
    }

    // endregion

    // region Subida local -> Google

    override suspend fun uploadUnsyncedItems(date: LocalDate): Result<Unit> = runGoogleCall("uploadUnsyncedItems") {
        val dateEpoch = date.toEpochDay()

        // Los fallos individuales ya se registran en cada upload; se continúa con el resto
        taskDao.getUnsyncedTasksByDate(dateEpoch).forEach { task ->
            uploadTaskToGoogle(task.id, task.title, task.description, date)
        }

        eventDao.getUnsyncedEventsByDate(dateEpoch).forEach { (event, subtasks) ->
            uploadEventToGoogle(
                eventId = event.id,
                title = event.title,
                description = event.description,
                location = event.location,
                startDate = date,
                startTime = event.startTime,
                endTime = event.endTime,
                category = event.categoryText,
                subtasks = subtasks.map { it.title }
            )
        }
    }

    override suspend fun uploadTaskToGoogle(
        taskId: String,
        title: String,
        notes: String?,
        date: LocalDate
    ): Result<Unit> = runGoogleCall("uploadTaskToGoogle") {
        val googleTask = Task().apply {
            setTitle(title)
            setNotes(notes)
            // Google Tasks solo guarda la fecha de vencimiento; la hora se descarta
            setDue("${date}T00:00:00.000Z")
        }
        val inserted = tasksService().tasks().insert(DEFAULT_TASK_LIST, googleTask).execute()
        taskDao.updateRemoteId(taskId, inserted.id)
    }

    override suspend fun updateTaskInGoogle(
        remoteId: String,
        title: String,
        notes: String?,
        isCompleted: Boolean
    ): Result<Unit> = runGoogleCall("updateTaskInGoogle") {
        val service = tasksService()
        // Las tareas sin lista conocida (creadas desde la app o previas a la migración) están en @default
        val taskListId = taskDao.getTaskByRemoteId(remoteId)?.taskListId ?: DEFAULT_TASK_LIST
        val task = service.tasks().get(taskListId, remoteId).execute().apply {
            setTitle(title)
            setNotes(notes)
            setStatus(if (isCompleted) TASK_STATUS_COMPLETED else TASK_STATUS_NEEDS_ACTION)
            setCompleted(if (isCompleted) DateTime(System.currentTimeMillis()).toStringRfc3339() else null)
        }
        service.tasks().update(taskListId, remoteId, task).execute()
    }

    override suspend fun uploadEventToGoogle(
        eventId: String,
        title: String,
        description: String?,
        location: String?,
        startDate: LocalDate,
        startTime: String,
        endTime: String,
        category: String?,
        subtasks: List<String>
    ): Result<Unit> = runGoogleCall("uploadEventToGoogle") {
        val local = eventDao.getEventById(eventId)
        val completedSubtasks = eventDao.getSubtasksForEvent(eventId).filter { it.isCompleted }.map { it.title }.toSet()
        val event = Event().apply {
            summary = if (local?.isCompleted == true) "$COMPLETED_MARK $title" else title
            this.description = buildDescription(description, subtasks, completedSubtasks)
            this.location = location
            colorId = category?.let { CATEGORY_TO_GOOGLE_COLOR[it] }
            if (isAllDayRange(startTime, endTime)) {
                // En Google el fin de un evento de día completo es exclusivo: el día siguiente
                start = EventDateTime().setDate(DateTime(startDate.toString()))
                end = EventDateTime().setDate(DateTime(startDate.plusDays(1).toString()))
            } else {
                start = eventDateTime(startDate, startTime)
                end = eventDateTime(startDate, endTime)
            }
        }

        val service = calendarService()
        val remoteId = local?.remoteId
        if (remoteId != null) {
            // Evento ya sincronizado (edición): patch para no duplicarlo en Google
            service.events().patch(PRIMARY_CALENDAR, remoteId, event).execute()
        } else {
            val inserted = service.events().insert(PRIMARY_CALENDAR, event).execute()
            eventDao.updateRemoteId(eventId, inserted.id)
        }
    }

    override suspend fun updateEventInGoogle(eventId: String): Result<Unit> {
        val event = eventDao.getEventById(eventId) ?: return Result.success(Unit)
        // Sin remoteId aún no está en Google: lo subirá uploadUnsyncedItems con el estado actual
        if (event.remoteId == null) return Result.success(Unit)
        return uploadEventToGoogle(
            eventId = eventId,
            title = event.title,
            description = event.description,
            location = event.location,
            startDate = LocalDate.ofEpochDay(event.date),
            startTime = event.startTime,
            endTime = event.endTime,
            category = event.categoryText,
            subtasks = eventDao.getSubtasksForEvent(eventId).map { it.title }
        )
    }

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

    private suspend fun credentialFor(scope: String): GoogleAccountCredential {
        val email = userDao.getUser().first()?.email ?: throw IllegalStateException("No user logged in")
        require(email.contains("@") && !email.equals("null", ignoreCase = true)) { "Invalid user email: $email" }
        return GoogleAccountCredential.usingOAuth2(context, listOf(scope)).apply {
            selectedAccount = Account(email, "com.google")
        }
    }

    private suspend fun tasksService(): Tasks =
        Tasks.Builder(transport, jsonFactory, credentialFor(TASKS_SCOPE))
            .setApplicationName(APP_NAME)
            .build()

    private suspend fun calendarService(): Calendar =
        Calendar.Builder(transport, jsonFactory, credentialFor(CALENDAR_SCOPE))
            .setApplicationName(APP_NAME)
            .build()

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
            else -> {
                val googleColor = GOOGLE_COLORS[colorId] ?: GOOGLE_DEFAULT_COLOR
                // El gris de Google (colorId 8) se sustituye por Slate400
                CATEGORY_GENERAL to if (colorId == "8") COLOR_SLATE_400 else googleColor
            }
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

    private fun buildDescription(
        description: String?,
        subtasks: List<String>,
        completedSubtasks: Set<String>
    ): String = buildString {
        description?.let { append(it).append("\n\n") }
        if (subtasks.isNotEmpty()) {
            append("Subtareas:\n")
            subtasks.forEach { append(if (it in completedSubtasks) "- [x] " else "- [ ] ").append(it).append('\n') }
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
        val today = LocalDate.now(zone)
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
        const val APP_NAME = "Syncro"
        const val TASKS_SCOPE = "https://www.googleapis.com/auth/tasks"
        const val CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar"
        const val PRIMARY_CALENDAR = "primary"
        const val DEFAULT_TASK_LIST = "@default"
        const val TASK_STATUS_COMPLETED = "completed"
        const val TASK_STATUS_NEEDS_ACTION = "needsAction"
        const val MAX_TASKS_PER_PAGE = 100
        const val TASKS_SYNC_MIN_INTERVAL_MS = 60_000L
        const val DEFAULT_TASK_TIME = "09:00"
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
            "Ocio" to "5"       // Plátano
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
