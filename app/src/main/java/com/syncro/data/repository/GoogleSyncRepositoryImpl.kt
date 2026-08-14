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

    override suspend fun syncTasks(): Result<Unit> = withContext(Dispatchers.IO) {
        val tag = "GoogleSync"
        try {
            val user = userDao.getUser().first() ?: return@withContext Result.failure(Exception("No user logged in"))
            Log.d(tag, "Syncing tasks for user email: '${user.email}'")
            
            if (user.email.isBlank() || !user.email.contains("@") || user.email.lowercase() == "null") {
                Log.e(tag, "Invalid user email for sync: '${user.email}'")
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
                            val taskEntity = TaskEntity(
                                remoteId = googleTask.id,
                                title = googleTask.title ?: "",
                                description = googleTask.notes ?: "",
                                date = parseGoogleDateToEpoch(googleTask.due),
                                time = parseGoogleTime(googleTask.due),
                                isCompleted = googleTask.status == "completed"
                            )
                            taskDao.insertTask(taskEntity)
                        }
                    }
                }
            }
            Log.d(tag, "Tasks sync completed")
            Result.success(Unit)
        } catch (e: UserRecoverableAuthIOException) {
            Log.w(tag, "User recoverable auth error during tasks sync", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(tag, "Error syncing tasks", e)
            Result.failure(e)
        }
    }

    override suspend fun syncCalendar(): Result<Unit> = withContext(Dispatchers.IO) {
        val tag = "GoogleSync"
        try {
            val user = userDao.getUser().first() ?: return@withContext Result.failure(Exception("No user logged in"))
            Log.d(tag, "Syncing calendar for user email: '${user.email}'")

            if (user.email.isBlank() || !user.email.contains("@") || user.email.lowercase() == "null") {
                Log.e(tag, "Invalid user email for sync: '${user.email}'")
                return@withContext Result.failure(Exception("Invalid user email: ${user.email}"))
            }

            val calendarScopes = listOf("https://www.googleapis.com/auth/calendar.readonly")
            val account = Account(user.email, "com.google")
            val credential = GoogleAccountCredential.usingOAuth2(context, calendarScopes)
            credential.selectedAccount = account

            val calendarService = Calendar.Builder(transport, jsonFactory, credential)
                .setApplicationName("Syncro")
                .build()

            // Sincronizar desde el inicio del día actual para no perder eventos que ya pasaron hoy
            val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val timeMin = com.google.api.client.util.DateTime(startOfDay)
            
            Log.d(tag, "Fetching events from: ${timeMin.toStringRfc3339()}")

            val eventsResponse = calendarService.events().list("primary")
                .setTimeMin(timeMin)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute()
            
            val events = eventsResponse.items
            Log.d(tag, "Found ${events?.size ?: 0} events in Google Calendar")

            if (events != null) {
                for (googleEvent in events) {
                    val startDateTime = googleEvent.start.dateTime ?: googleEvent.start.date
                    val localDate = parseGoogleDateToLocalDate(startDateTime)
                    
                    Log.d(tag, "Event: ${googleEvent.summary} - Date: $localDate")

                    val eventEntity = EventEntity(
                        remoteId = googleEvent.id,
                        title = googleEvent.summary ?: "",
                        description = googleEvent.description,
                        date = localDate.toEpochDay(), 
                        startTime = formatGoogleDateTime(googleEvent.start.dateTime),
                        endTime = formatGoogleDateTime(googleEvent.end.dateTime),
                        categoryText = "Google Calendar",
                        categoryColor = 0xFF4285F4.toInt(),
                        priority = "MEDIUM",
                        isAllDay = googleEvent.start.dateTime == null,
                        location = googleEvent.location
                    )
                    eventDao.insertEvent(eventEntity)
                }
            }
            Log.d(tag, "Calendar sync completed")
            Result.success(Unit)
        } catch (e: UserRecoverableAuthIOException) {
            Log.w(tag, "User recoverable auth error during calendar sync", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(tag, "Error syncing calendar", e)
            Result.failure(e)
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
            // Usamos el sistema de zona horaria por defecto para la conversión
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
