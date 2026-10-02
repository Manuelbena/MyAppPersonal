package com.syncro.testutil

import com.syncro.domain.model.AppSettings
import com.syncro.domain.model.DailyFocus
import com.syncro.domain.model.DataLossSummary
import com.syncro.domain.model.Movement
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.repository.AccountDataRepository
import com.syncro.domain.repository.DailyFocusRepository
import com.syncro.domain.repository.SettingsRepository
import com.syncro.domain.repository.EventRepository
import com.syncro.domain.repository.GoogleSyncRepository
import com.syncro.domain.repository.MovementRepository
import com.syncro.domain.repository.NoteRepository
import com.syncro.domain.repository.TaskRepository
import com.syncro.domain.repository.UserRepository
import com.syncro.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth

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

    override suspend fun deleteTask(taskId: String) {
        log.calls += "deleteTask($taskId)"
        tasks.update { it - taskId }
    }

    override suspend fun moveTask(taskId: String, date: LocalDate) {
        log.calls += "moveTask($taskId, $date)"
        tasks.update { all ->
            val task = all[taskId] ?: return@update all
            all + (taskId to task.copy(date = date))
        }
    }

    override fun getUnfinishedTasksUntil(date: LocalDate): Flow<List<SyncroItem.Task>> =
        tasks.map { all -> all.values.filter { !it.isCompleted && !it.date.isAfter(date) }.sortedWith(compareBy({ it.date }, { it.time })) }
}

class FakeEventRepository(private val log: CallLog = CallLog()) : EventRepository {
    val events = MutableStateFlow<Map<String, SyncroItem.Event>>(emptyMap())

    // Mismo contrato que el repositorio real: los eventos que tocan el día, no solo los que empiezan
    override fun getEventsByDate(date: LocalDate): Flow<List<SyncroItem.Event>> =
        events.map { all -> all.values.filter { !it.date.isAfter(date) && !it.endDate.isBefore(date) } }

    override fun getEventsInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<SyncroItem.Event>> =
        events.map { all -> all.values.filter { !it.date.isAfter(endDate) && !it.endDate.isBefore(startDate) } }

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

    override suspend fun deleteEvent(eventId: String) {
        log.calls += "deleteEvent($eventId)"
        events.update { it - eventId }
    }
}

/**
 * Simula Google: anota qué se sube y sincroniza. [isOffline] simula que no hay conexión y
 * [syncFailure] que la descarga falla con un error concreto (por ejemplo, falta de permisos).
 */
class FakeGoogleSyncRepository(private val log: CallLog = CallLog()) : GoogleSyncRepository {
    var isOffline = false
    var syncFailure: Throwable? = null
    val pushedTaskIds = mutableListOf<String>()
    val pushedEventIds = mutableListOf<String>()

    private fun result(): Result<Unit> =
        if (isOffline) Result.failure(IOException("Sin conexión")) else Result.success(Unit)

    override suspend fun syncTasks(force: Boolean): Result<Unit> {
        log.calls += "syncTasks"
        return syncFailure?.let { Result.failure(it) } ?: result()
    }

    override suspend fun syncCalendar(startDate: LocalDate, endDate: LocalDate): Result<Unit> {
        log.calls += "syncCalendar($startDate..$endDate)"
        return syncFailure?.let { Result.failure(it) } ?: result()
    }

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

    override suspend fun pushPendingChanges(): Result<Unit> {
        log.calls += "pushPendingChanges"
        return result()
    }
}

class FakeUserRepository : UserRepository {
    val user = MutableStateFlow<User?>(null)

    override fun getUser(): Flow<User?> = user

    override suspend fun saveUser(user: User) {
        this.user.value = user
    }

    override suspend fun clearUser() {
        user.value = null
    }
}

class FakeNoteRepository : NoteRepository {
    val notes = MutableStateFlow<Map<String, SyncroItem.Note>>(emptyMap())

    override fun getAllNotes(): Flow<List<SyncroItem.Note>> =
        notes.map { all -> all.values.sortedByDescending { it.createdAt } }

    override suspend fun insertNote(note: SyncroItem.Note) {
        notes.update { it + (note.id to note) }
    }

    override suspend fun deleteNote(id: String) {
        notes.update { it - id }
    }

    override suspend fun getNoteById(id: String): SyncroItem.Note? = notes.value[id]
}

/** Como el DAO real: devuelve los del mes y los mensuales que empezaron antes (un superconjunto). */
class FakeMovementRepository : MovementRepository {
    val movements = MutableStateFlow<Map<String, Movement>>(emptyMap())

    override fun observeForMonth(month: YearMonth): Flow<List<Movement>> = movements.map { all ->
        all.values.filter { YearMonth.from(it.date) == month || (it.repeatsMonthly && !it.date.isAfter(month.atEndOfMonth())) }
    }

    override suspend fun insertMovement(movement: Movement) {
        movements.update { it + (movement.id to movement) }
    }

    override suspend fun deleteMovement(id: String) {
        movements.update { it - id }
    }
}

class FakeDailyFocusRepository : DailyFocusRepository {
    val focus = MutableStateFlow<Map<LocalDate, DailyFocus>>(emptyMap())

    override fun getFocus(date: LocalDate): Flow<DailyFocus?> = focus.map { it[date] }

    override fun getFocusSince(date: LocalDate): Flow<List<DailyFocus>> =
        focus.map { all -> all.values.filter { !it.date.isBefore(date) }.sortedBy { it.date } }

    override suspend fun saveFocus(focus: DailyFocus) {
        this.focus.update { it + (focus.date to focus) }
    }
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    val current = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = current

    override suspend fun save(settings: AppSettings) {
        current.value = settings
    }
}

class FakeAccountDataRepository(private val log: CallLog = CallLog()) : AccountDataRepository {
    var summary = DataLossSummary(unsyncedChanges = 0, notes = 0)

    override suspend fun dataLossSummary(): DataLossSummary = summary

    override suspend fun clearAccountData() {
        log.calls += "clearAccountData"
    }
}
