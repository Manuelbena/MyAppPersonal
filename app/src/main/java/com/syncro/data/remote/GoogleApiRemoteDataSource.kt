package com.syncro.data.remote

import android.accounts.Account
import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.model.Task
import com.syncro.data.local.dao.UserDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Implementación real de [GoogleRemoteDataSource] con la cuenta del usuario con sesión iniciada. */
class GoogleApiRemoteDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userDao: UserDao
) : GoogleRemoteDataSource {

    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val transport = NetHttpTransport()

    override suspend fun listTaskListIds(): List<String> =
        tasksService().tasklists().list().execute().items.orEmpty().map { it.id }

    override suspend fun listTasks(taskListId: String): List<Task> {
        val service = tasksService()
        val tasks = mutableListOf<Task>()
        var pageToken: String? = null
        do {
            val response = service.tasks().list(taskListId)
                // Las apps de Google ocultan las tareas completadas; sin esto se darían por borradas
                .setShowHidden(true)
                .setMaxResults(MAX_TASKS_PER_PAGE)
                .setPageToken(pageToken)
                .execute()
            tasks += response.items.orEmpty()
            pageToken = response.nextPageToken
        } while (pageToken != null)
        return tasks
    }

    override suspend fun insertTask(taskListId: String, task: Task): Task =
        tasksService().tasks().insert(taskListId, task).execute()

    override suspend fun patchTask(taskListId: String, taskId: String, task: Task) {
        tasksService().tasks().patch(taskListId, taskId, task).execute()
    }

    override suspend fun deleteTask(taskListId: String, taskId: String) {
        tasksService().tasks().delete(taskListId, taskId).execute()
    }

    override suspend fun listEvents(timeMin: DateTime, timeMax: DateTime): List<Event> {
        val service = calendarService()
        val events = mutableListOf<Event>()
        var pageToken: String? = null
        do {
            val response = service.events().list(PRIMARY_CALENDAR)
                .setTimeMin(timeMin)
                .setTimeMax(timeMax)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .setPageToken(pageToken)
                .execute()
            events += response.items.orEmpty()
            pageToken = response.nextPageToken
        } while (pageToken != null)
        return events
    }

    override suspend fun insertEvent(event: Event): Event =
        calendarService().events().insert(PRIMARY_CALENDAR, event).execute()

    override suspend fun patchEvent(eventId: String, event: Event) {
        // Patch y no update: conserva en Google lo que la app no gestiona (invitados, avisos…)
        calendarService().events().patch(PRIMARY_CALENDAR, eventId, event).execute()
    }

    override suspend fun deleteEvent(eventId: String) {
        calendarService().events().delete(PRIMARY_CALENDAR, eventId).execute()
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

    private companion object {
        const val APP_NAME = "Syncro"
        const val TASKS_SCOPE = "https://www.googleapis.com/auth/tasks"
        const val CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar"
        const val PRIMARY_CALENDAR = "primary"
        const val MAX_TASKS_PER_PAGE = 100
    }
}
