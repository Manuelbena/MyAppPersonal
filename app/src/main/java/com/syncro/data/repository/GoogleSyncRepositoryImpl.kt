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

            val tasksScopes = listOf("https://www.googleapis.com/auth/tasks.readonly")
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
                            
                            val taskEntity = TaskEntity(
                                id = "${taskDateEpoch}_${title}_${time}",
                                remoteId = googleTask.id,
                                title = title,
                                description = googleTask.notes ?: "",
                                date = taskDateEpoch,
                                time = time,
                                isCompleted = googleTask.status == "completed"
                            )
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

            val calendarScopes = listOf("https://www.googleapis.com/auth/calendar.readonly")
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

                    val eventId = "${dateEpoch}_${title}_${startTime}"
                    
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
                    
                    val cleanTitle = title
                        .replace("[x]", "", ignoreCase = true)
                        .replace("[ ]", "", ignoreCase = true)
                        .replace("✅", "")
                        .trim()

                    val colorId = googleEvent.colorId
                    val categoryColor = mapGoogleColorToHex(colorId)
                    
                    // Lógica de categoría dinámica basada en el color o título
                    val (categoryName, finalColor) = when {
                        title.contains("Trabajo", ignoreCase = true) || colorId == "6" -> "Trabajo" to 0xFFE67C73.toInt() // Mandarina/Rojizo para Trabajo
                        title.contains("Cita", ignoreCase = true) || title.contains("Médico", ignoreCase = true) || colorId == "11" -> "Personal" to 0xFF7AE7BF.toInt() // Esmeralda para Personal
                        else -> "General" to categoryColor
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
