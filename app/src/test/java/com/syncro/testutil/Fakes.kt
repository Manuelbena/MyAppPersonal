package com.syncro.testutil

import com.syncro.domain.model.AppSettings
import com.syncro.domain.model.Budget
import com.syncro.domain.model.DailyFocus
import com.syncro.domain.model.DataLossSummary
import com.syncro.domain.model.Movement
import com.syncro.domain.model.RepeatSeries
import com.syncro.domain.model.ArgbColor
import com.syncro.domain.model.MAIN_ACCOUNT_ID
import com.syncro.domain.model.MAIN_ACCOUNT_NAME
import com.syncro.domain.model.SavingsAccount
import com.syncro.domain.model.SavingsAccounts
import com.syncro.domain.model.SavingsPeriod
import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.repository.AccountDataRepository
import com.syncro.domain.repository.BudgetRepository
import com.syncro.domain.repository.ConnectivityRepository
import com.syncro.domain.repository.DailyFocusRepository
import com.syncro.domain.repository.DailyQuoteRepository
import com.syncro.domain.repository.SettingsRepository
import com.syncro.domain.repository.EventRepository
import com.syncro.domain.repository.GoogleSyncRepository
import com.syncro.domain.repository.MovementRepository
import com.syncro.domain.repository.NoteRepository
import com.syncro.domain.repository.RepeatSeriesRepository
import com.syncro.domain.repository.SavingsAccountRepository
import com.syncro.domain.repository.TaskRepository
import com.syncro.domain.repository.UserRepository
import com.syncro.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

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

    /** Las borradas siguen guardadas (como la marca isDeleted de Room) hasta que se suben. */
    val deletedTasks = MutableStateFlow<Map<String, SyncroItem.Task>>(emptyMap())

    override suspend fun deleteTask(taskId: String) {
        log.calls += "deleteTask($taskId)"
        val task = tasks.value[taskId] ?: return
        deletedTasks.update { it + (taskId to task) }
        tasks.update { it - taskId }
    }

    override suspend fun restoreTask(taskId: String): Boolean {
        log.calls += "restoreTask($taskId)"
        val task = deletedTasks.value[taskId] ?: return false
        deletedTasks.update { it - taskId }
        tasks.update { it + (taskId to task) }
        return true
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

    override suspend fun getTaskIdsInSeries(seriesId: String, from: LocalDate): List<String> =
        tasks.value.values.filter { it.seriesId == seriesId && !it.date.isBefore(from) }.map { it.id }

    override fun observeTasksWithReminder(from: LocalDate): Flow<List<SyncroItem.Task>> =
        tasks.map { all -> all.values.filter { it.reminderMinutes != null && !it.isCompleted && !it.date.isBefore(from) } }
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

    val deletedEvents = MutableStateFlow<Map<String, SyncroItem.Event>>(emptyMap())

    override suspend fun deleteEvent(eventId: String) {
        log.calls += "deleteEvent($eventId)"
        val event = events.value[eventId] ?: return
        deletedEvents.update { it + (eventId to event) }
        events.update { it - eventId }
    }

    override suspend fun restoreEvent(eventId: String): Boolean {
        log.calls += "restoreEvent($eventId)"
        val event = deletedEvents.value[eventId] ?: return false
        deletedEvents.update { it - eventId }
        events.update { it + (eventId to event) }
        return true
    }

    override suspend fun getEventIdsInSeries(seriesId: String, from: LocalDate): List<String> =
        events.value.values.filter { it.seriesId == seriesId && !it.date.isBefore(from) }.map { it.id }

    override fun observeEventsWithReminder(from: LocalDate): Flow<List<SyncroItem.Event>> =
        events.map { all -> all.values.filter { it.reminderMinutes != null && !it.isCompleted && !it.date.isBefore(from) } }
}

class FakeRepeatSeriesRepository : RepeatSeriesRepository {
    val series = MutableStateFlow<Map<String, RepeatSeries>>(emptyMap())

    override suspend fun getAllSeries(): List<RepeatSeries> = series.value.values.toList()

    override suspend fun getSeries(id: String): RepeatSeries? = series.value[id]

    override suspend fun saveSeries(series: RepeatSeries) {
        this.series.update { it + (series.id to series) }
    }

    override suspend fun deleteSeries(id: String) {
        series.update { it - id }
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

    /** Lo que contaría Room; los tests lo fijan a mano. */
    val pendingChanges = MutableStateFlow(0)

    override fun observePendingChangesCount(): Flow<Int> = pendingChanges
}

class FakeConnectivityRepository : ConnectivityRepository {
    val online = MutableStateFlow(true)

    override val isOnline: Flow<Boolean> = online
}

/** Un reloj que el test puede adelantar (p. ej. para pasar la medianoche con la app abierta). */
class MutableClock(var instant: Instant, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
    override fun instant(): Instant = instant
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

/** Como el DAO real: devuelve los del periodo y los mensuales que empezaron antes (un superconjunto). */
class FakeMovementRepository : MovementRepository {
    val movements = MutableStateFlow<Map<String, Movement>>(emptyMap())

    override fun observeForPeriod(period: SavingsPeriod, accountId: String?): Flow<List<Movement>> = movements.map { all ->
        all.values.filter { (it.date in period || (it.repeatsMonthly && !it.date.isAfter(period.end))) && (accountId == null || it.accountId == accountId) }
    }

    override suspend fun getAllMovements(): List<Movement> = movements.value.values.sortedBy { it.date }

    override suspend fun insertMovement(movement: Movement) {
        movements.update { it + (movement.id to movement) }
    }

    override suspend fun deleteMovement(id: String) {
        movements.update { it - id }
    }
}

class FakeDailyQuoteRepository : DailyQuoteRepository {
    override val hiddenOn = MutableStateFlow<LocalDate?>(null)

    override suspend fun hide(date: LocalDate) {
        hiddenOn.value = date
    }
}

/** Como la tabla: uno por cuenta y categoría. */
class FakeBudgetRepository : BudgetRepository {
    val budgets = MutableStateFlow<Map<Pair<String, MovementCategory>, Budget>>(emptyMap())

    override fun observeBudgets(accountId: String?): Flow<List<Budget>> =
        budgets.map { all -> all.values.filter { accountId == null || it.accountId == accountId } }

    override suspend fun saveBudget(budget: Budget) {
        budgets.update { it + ((budget.accountId to budget.category) to budget) }
    }

    override suspend fun deleteBudget(accountId: String, category: MovementCategory) {
        budgets.update { it - (accountId to category) }
    }
}

/** Como el real: siempre hay al menos la principal, y una sola es la que se ve. */
class FakeSavingsAccountRepository(private val movements: FakeMovementRepository? = null, private val budgets: FakeBudgetRepository? = null) :
    SavingsAccountRepository {
    val accounts = MutableStateFlow(listOf(SavingsAccount(MAIN_ACCOUNT_ID, MAIN_ACCOUNT_NAME, ArgbColor(0xFF10B981))))
    val activeId = MutableStateFlow(MAIN_ACCOUNT_ID)

    override fun observeAccounts(): Flow<SavingsAccounts> = combine(accounts, activeId) { all, active ->
        SavingsAccounts(all, all.firstOrNull { it.id == active } ?: all.first())
    }

    override suspend fun saveAccount(account: SavingsAccount) {
        accounts.update { all -> if (all.any { it.id == account.id }) all.map { if (it.id == account.id) account else it } else all + account }
    }

    override suspend fun selectAccount(id: String) {
        activeId.value = id
    }

    override suspend fun deleteAccount(id: String) {
        accounts.update { all -> all.filter { it.id != id } }
        movements?.movements?.update { all -> all.filterValues { it.accountId != id } }
        budgets?.budgets?.update { all -> all.filterValues { it.accountId != id } }
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
