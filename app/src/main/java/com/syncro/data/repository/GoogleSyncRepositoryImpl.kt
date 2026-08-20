package com.syncro.data.repository

import android.accounts.Account
import android.content.Context
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.tasks.Tasks
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.api.services.tasks.model.Task
import com.syncro.data.local.dao.EventDao
import com.syncro.data.local.dao.TaskDao
import com.syncro.data.local.dao.UserDao
import com.syncro.data.local.entity.EventEntity
import com.syncro.data.local.entity.SubtaskEntity
import com.syncro.data.local.entity.TaskEntity
import com.syncro.domain.repository.GoogleSyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class GoogleSyncRepositoryImpl @Inject constructor(
    private val context: Context,
    private val userDao: UserDao,
    private val taskDao: TaskDao,
    private val eventDao: EventDao,
) : GoogleSyncRepository {

    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val transport = NetHttpTransport()

    override suspend fun syncTasks(date: LocalDate): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUser().first() ?: return@withContext Result.failure(Exception("No user logged in"))
            
            if (user.email.isBlank() || !user.email.contains("@") || user.email.lowercase() == "null") {
                return@withContext Result.failure(Exception("Invalid user email: ${user.email}"))
            }

            val tasksScopes = listOf("https://www.googleapis.com/auth/tasks")
            val account = Account(user.email, "com.google")
            val credential = GoogleAccountCredential.usingOAuth2(context, tasksScopes)
            credential.selectedAccount = account

            val tasksService = Tasks.Builder(transport, jsonFactory, credential)
                .setApplicationName("Syncro")
                .build()

            val taskListsResponse = tasksService.tasklists().list().execute()
            val taskLists = taskListsResponse.items

            if (taskLists != null) {
                for (taskList in taskLists) {
                    val tasksResponse = tasksService.tasks().list(taskList.id).execute()
                    val googleTasks = tasksResponse.items
                    if (googleTasks != null) {
                        for (googleTask in googleTasks) {
                            val taskDateEpoch = parseGoogleDateToEpoch(googleTask.due)
                            val title = googleTask.title ?: ""
                            val time = parseGoogleTime(googleTask.due)
                            
                            // 1. Intentar buscar por remoteId primero
                            val existingTaskByRemote = taskDao.getTaskByRemoteId(googleTask.id)
                            
                            val taskEntity = if (existingTaskByRemote != null) {
                                existingTaskByRemote.copy(
                                    title = title,
                                    description = googleTask.notes ?: "",
                                    date = taskDateEpoch,
                                    time = time,
                                    isCompleted = googleTask.status == "completed"
                                )
                            } else {
                                TaskEntity(
                                    id = "${taskDateEpoch}_${title}_${time}",
                                    remoteId = googleTask.id,
                                    title = title,
                                    description = googleTask.notes ?: "",
                                    date = taskDateEpoch,
                                    time = time,
                                    isCompleted = googleTask.status == "completed",
                                    categoryText = "General",
                                    categoryColor = 0xFF94A3B8.toInt() // Slate400
                                )
                            }
                            taskDao.insertTask(taskEntity)
                        }
                    }
                }
            }
            Result.success(Unit)
        } catch (e: UserRecoverableAuthIOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncCalendar(date: LocalDate): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUser().first() ?: return@withContext Result.failure(Exception("No user logged in"))

            if (user.email.isBlank() || !user.email.contains("@") || user.email.lowercase() == "null") {
                return@withContext Result.failure(Exception("Invalid user email: ${user.email}"))
            }

            val calendarScopes = listOf("https://www.googleapis.com/auth/calendar")
            val account = Account(user.email, "com.google")
            val credential = GoogleAccountCredential.usingOAuth2(context, calendarScopes)
            credential.selectedAccount = account

            val calendarService = Calendar.Builder(transport, jsonFactory, credential)
                .setApplicationName("Syncro")
                .build()

            val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            
            val timeMin = com.google.api.client.util.DateTime(startOfDay)
            val timeMax = com.google.api.client.util.DateTime(endOfDay)

            val eventsResponse = calendarService.events().list("primary")
                .setTimeMin(timeMin)
                .setTimeMax(timeMax)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute()
            
            val events = eventsResponse.items
            if (events != null) {
                for (googleEvent in events) {
                    val startDateTime = googleEvent.start.dateTime ?: googleEvent.start.date
                    val localDate = parseGoogleDateToLocalDate(startDateTime)
                    val dateEpoch = localDate.toEpochDay()
                    val title = googleEvent.summary ?: ""
                    val startTime = formatGoogleDateTime(googleEvent.start.dateTime)
                    val rawDescription = googleEvent.description ?: ""

                    // 1. Limpiar el título de emojis o marcas de completado
                    val cleanTitle = title
                        .replace("[x]", "", ignoreCase = true)
                        .replace("[ ]", "", ignoreCase = true)
                        .replace("✅", "")
                        .trim()

                    // 2. Intentar buscar por remoteId para evitar duplicados si el título cambió
                    val existingEventByRemote = eventDao.getEventByRemoteId(googleEvent.id)
                    
                    val eventId = existingEventByRemote?.id ?: "${dateEpoch}_${cleanTitle}_${startTime}"
                    
                    // Extraer subtareas y limpiar descripción
                    var cleanDescription = rawDescription
                    val subtasks = mutableListOf<SubtaskEntity>()
                    
                    if (rawDescription.contains("subtareas:", ignoreCase = true)) {
                        val parts = rawDescription.split(Regex("subtareas:", RegexOption.IGNORE_CASE))
                        cleanDescription = parts[0].trim()
                        
                        if (parts.size > 1) {
                            val subtasksPart = parts[1]
                            val lines = subtasksPart.lines()
                            for (line in lines) {
                                val trimmedLine = line.trim()
                                if (trimmedLine.isEmpty() || trimmedLine.contains("vacio", ignoreCase = true)) continue

                                if (trimmedLine.contains("[x]", ignoreCase = true) || trimmedLine.startsWith("x ", ignoreCase = true)) {
                                    val subtaskTitle = trimmedLine.replace("[x]", "", ignoreCase = true).removePrefix("x ").removePrefix("X ").trim()
                                    if (subtaskTitle.isNotEmpty()) {
                                        subtasks.add(SubtaskEntity(eventId = eventId, title = subtaskTitle, isCompleted = true))
                                    }
                                } else {
                                    // Cualquier otra línea la tratamos como subtarea pendiente
                                    val subtaskTitle = trimmedLine.replace("[ ]", "", ignoreCase = true).removePrefix("- ").removePrefix("• ").trim()
                                    if (subtaskTitle.isNotEmpty()) {
                                        subtasks.add(SubtaskEntity(eventId = eventId, title = subtaskTitle, isCompleted = false))
                                    }
                                }
                            }
                        }
                    }

                    // Lógica para saber si el evento principal está completado
                    val isEventCompleted = title.contains("[x]", ignoreCase = true) || 
                                         title.startsWith("Cancelado", ignoreCase = true) ||
                                         title.startsWith("Done", ignoreCase = true) ||
                                         title.contains("✅")
                    
                    val colorId = googleEvent.colorId
                    val categoryColor = mapGoogleColorToHex(colorId)
                    
                    // Lógica de categoría dinámica basada en el color o título
                    val (categoryName, finalColor) = when {
                        title.contains("Trabajo", ignoreCase = true) || colorId == "6" -> "Trabajo" to 0xFF6366F1.toInt() // Indigo500 (Trabajo)
                        title.contains("Salud", ignoreCase = true) || colorId == "11" -> "Salud" to 0xFFFF5252.toInt() // Rojo Salud
                        title.contains("Ocio", ignoreCase = true) || colorId == "5" -> "Ocio" to 0xFFF59E0B.toInt() // Amber500 (Ocio)
                        title.contains("Personal", ignoreCase = true) || title.contains("Cita", ignoreCase = true) || title.contains("Médico", ignoreCase = true) || colorId == "2" -> "Personal" to 0xFF10B981.toInt() // Emerald500 (Personal)
                        else -> "General" to if (categoryColor == 0xFFE1E1E1.toInt()) 0xFF94A3B8.toInt() else categoryColor // Slate400 si es muy gris
                    }

                    val eventEntity = EventEntity(
                        id = eventId,
                        remoteId = googleEvent.id,
                        title = cleanTitle,
                        description = cleanDescription,
                        date = dateEpoch, 
                        startTime = startTime,
                        endTime = formatGoogleDateTime(googleEvent.end.dateTime),
                        categoryText = categoryName,
                        categoryColor = finalColor,
                        priority = "MEDIUM",
                        isAllDay = googleEvent.start.dateTime == null,
                        location = googleEvent.location,
                        isCompleted = isEventCompleted
                    )
                    
                    if (subtasks.isEmpty()) {
                        eventDao.insertEvent(eventEntity)
                    } else {
                        eventDao.insertEventWithSubtasks(eventEntity, subtasks)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: UserRecoverableAuthIOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadUnsyncedItems(date: LocalDate): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dateEpoch = date.toEpochDay()
            
            // 1. Subir Tareas locales no sincronizadas
            val unsyncedTasks = taskDao.getUnsyncedTasksByDate(dateEpoch)
            for (task in unsyncedTasks) {
                uploadTaskToGoogle(task.id, task.title, task.description, date)
            }

            // 2. Subir Eventos locales no sincronizados
            val unsyncedEvents = eventDao.getUnsyncedEventsByDate(dateEpoch)
            for (eventWithSubtasks in unsyncedEvents) {
                val event = eventWithSubtasks.event
                val subtasks = eventWithSubtasks.subtasks.map { it.title }
                uploadEventToGoogle(
                    eventId = event.id,
                    title = event.title,
                    description = event.description,
                    location = event.location,
                    startDate = date,
                    startTime = event.startTime,
                    endTime = event.endTime,
                    category = event.categoryText,
                    subtasks = subtasks
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadTaskToGoogle(taskId: String, title: String, notes: String?, date: LocalDate): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUser().first() ?: return@withContext Result.failure(Exception("No user logged in"))
            val tasksScopes = listOf("https://www.googleapis.com/auth/tasks")
            val credential = GoogleAccountCredential.usingOAuth2(context, tasksScopes)
            credential.selectedAccount = Account(user.email, "com.google")

            val tasksService = Tasks.Builder(transport, jsonFactory, credential).setApplicationName("Syncro").build()
            
            val googleTask = Task().apply {
                setTitle(title)
                setNotes(notes)
                // Para simplificar, ponemos fecha de vencimiento a las 9 AM del día elegido
                val dueDateTime = OffsetDateTime.of(date.atTime(9, 0), ZoneId.systemDefault().rules.getOffset(Instant.now()))
                setDue(dueDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            }

            // Usamos la lista principal (@default)
            val insertedTask = tasksService.tasks().insert("@default", googleTask).execute()
            
            // Actualizar el remoteId en la base de datos local usando el ID real
            taskDao.getTaskById(taskId)?.let { entity ->
                taskDao.updateTask(entity.copy(remoteId = insertedTask.id))
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateTaskInGoogle(remoteId: String, title: String, notes: String?, isCompleted: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUser().first() ?: return@withContext Result.failure(Exception("No user logged in"))
            val tasksScopes = listOf("https://www.googleapis.com/auth/tasks")
            val credential = GoogleAccountCredential.usingOAuth2(context, tasksScopes)
            credential.selectedAccount = Account(user.email, "com.google")

            val tasksService = Tasks.Builder(transport, jsonFactory, credential).setApplicationName("Syncro").build()
            
            val taskToUpdate = tasksService.tasks().get("@default", remoteId).execute()
            taskToUpdate.setTitle(title)
            taskToUpdate.setNotes(notes)
            taskToUpdate.setStatus(if (isCompleted) "completed" else "needsAction")
            if (isCompleted) {
                // Para Google Tasks, la fecha de completado debe enviarse como RFC3339 String o manejarse vía status
                taskToUpdate.setCompleted(com.google.api.client.util.DateTime(System.currentTimeMillis()).toStringRfc3339())
            } else {
                taskToUpdate.setCompleted(null)
            }

            tasksService.tasks().update("@default", remoteId, taskToUpdate).execute()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUser().first() ?: return@withContext Result.failure(Exception("No user logged in"))
            val calendarScopes = listOf("https://www.googleapis.com/auth/calendar")
            val credential = GoogleAccountCredential.usingOAuth2(context, calendarScopes)
            credential.selectedAccount = Account(user.email, "com.google")

            val calendarService = Calendar.Builder(transport, jsonFactory, credential).setApplicationName("Syncro").build()

            val startLT = LocalTime.parse(startTime)
            val endLT = LocalTime.parse(endTime)
            
            val startDT = OffsetDateTime.of(startDate.atTime(startLT), ZoneId.systemDefault().rules.getOffset(Instant.now()))
            val endDT = OffsetDateTime.of(startDate.atTime(endLT), ZoneId.systemDefault().rules.getOffset(Instant.now()))

            // Preparar descripción con subtareas si existen
            val finalDescription = StringBuilder()
            description?.let { finalDescription.append(it).append("\n\n") }
            if (subtasks.isNotEmpty()) {
                finalDescription.append("Subtareas:\n")
                subtasks.forEach { finalDescription.append("- [ ] $it\n") }
            }

            val event = Event().apply {
                summary = title
                this.description = finalDescription.toString().trim()
                this.location = location
                // Asignar el color de Google basado en la categoría de la app
                colorId = when (category) {
                    "Trabajo" -> "6"   // Mandarina
                    "Personal" -> "2"  // Salvia/Verde
                    "Salud" -> "11"    // Tomate/Rojo
                    "Ocio" -> "5"      // Plátano/Amarillo
                    else -> null
                }
                start = EventDateTime().setDateTime(com.google.api.client.util.DateTime(startDT.toInstant().toEpochMilli()))
                end = EventDateTime().setDateTime(com.google.api.client.util.DateTime(endDT.toInstant().toEpochMilli()))
            }

            val insertedEvent = calendarService.events().insert("primary", event).execute()
            
            // Actualizar el remoteId localmente usando el ID real
            eventDao.getEventById(eventId)?.let { entity ->
                eventDao.updateEvent(entity.copy(remoteId = insertedEvent.id))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateEventInGoogle(
        remoteId: String,
        title: String,
        description: String?,
        isCompleted: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUser().first() ?: return@withContext Result.failure(Exception("No user logged in"))
            val calendarScopes = listOf("https://www.googleapis.com/auth/calendar")
            val credential = GoogleAccountCredential.usingOAuth2(context, calendarScopes)
            credential.selectedAccount = Account(user.email, "com.google")

            val calendarService = Calendar.Builder(transport, jsonFactory, credential).setApplicationName("Syncro").build()

            val eventToUpdate = calendarService.events().get("primary", remoteId).execute()
            
            // Marcar como completado en el título si es necesario
            val newTitle = if (isCompleted && !title.contains("✅")) "✅ $title" else title
            eventToUpdate.summary = newTitle
            eventToUpdate.description = description

            calendarService.events().update("primary", remoteId, eventToUpdate).execute()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mapGoogleColorToHex(colorId: String?): Int {
        return when (colorId) {
            "1" -> 0xFFA4BDFC.toInt()
            "2" -> 0xFF7AE7BF.toInt()
            "3" -> 0xFFBDADFF.toInt()
            "4" -> 0xFFFF887C.toInt()
            "5" -> 0xFFFBD75B.toInt()
            "6" -> 0xFFFFB878.toInt()
            "7" -> 0xFF46D6DB.toInt()
            "8" -> 0xFFE1E1E1.toInt()
            "9" -> 0xFF5484ED.toInt()
            "10" -> 0xFF51B749.toInt()
            "11" -> 0xFFDC2127.toInt()
            else -> 0xFF4285F4.toInt()
        }
    }

    private fun parseGoogleDateToEpoch(due: String?): Long {
        if (due == null) return LocalDate.now().toEpochDay()
        return try {
            val odt = OffsetDateTime.parse(due)
            odt.toLocalDate().toEpochDay()
        } catch (_: Exception) {
            LocalDate.now().toEpochDay()
        }
    }

    private fun parseGoogleDateToLocalDate(googleDateTime: com.google.api.client.util.DateTime): LocalDate {
        return try {
            val instant = Instant.ofEpochMilli(googleDateTime.value)
            instant.atZone(ZoneId.systemDefault()).toLocalDate()
        } catch (_: Exception) {
            LocalDate.now()
        }
    }

    private fun parseGoogleTime(due: String?): String {
        if (due == null) return "09:00"
        return try {
            val odt = OffsetDateTime.parse(due)
            odt.format(DateTimeFormatter.ofPattern("HH:mm"))
        } catch (_: Exception) {
            "09:00"
        }
    }

    private fun formatGoogleDateTime(dateTime: com.google.api.client.util.DateTime?): String {
        if (dateTime == null) return "00:00"
        return try {
            val odt = OffsetDateTime.parse(dateTime.toStringRfc3339())
            odt.format(DateTimeFormatter.ofPattern("HH:mm"))
        } catch (_: Exception) {
            "00:00"
        }
    }
}
