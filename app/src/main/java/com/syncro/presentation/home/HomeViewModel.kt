package com.syncro.presentation.home

import com.syncro.presentation.theme.toArgbColor
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import androidx.compose.ui.graphics.Color
import com.syncro.domain.model.Priority
import com.syncro.domain.model.Recurrence
import com.syncro.domain.model.RepeatScope
import com.syncro.domain.model.Quote
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.usecase.GetTimelineUseCase
import com.syncro.domain.usecase.GenerateRepeatsUseCase
import com.syncro.domain.usecase.SaveEventUseCase
import com.syncro.domain.usecase.SaveTaskUseCase
import com.syncro.domain.usecase.SyncGoogleCalendarUseCase
import com.syncro.domain.usecase.SyncGoogleTasksUseCase
import com.syncro.domain.usecase.ToggleEventCompletionUseCase
import com.syncro.domain.usecase.ToggleSubtaskCompletionUseCase
import com.syncro.domain.usecase.ToggleTaskCompletionUseCase
import com.syncro.domain.usecase.DeleteEventUseCase
import com.syncro.domain.usecase.DeleteTaskUseCase
import com.syncro.domain.usecase.DeleteNoteUseCase
import com.syncro.domain.usecase.GetNotesUseCase
import com.syncro.domain.usecase.GetDailyFocusUseCase
import com.syncro.domain.usecase.HideDailyQuoteUseCase
import com.syncro.domain.usecase.ObserveDailyQuoteUseCase
import com.syncro.domain.usecase.GetLocalUserUseCase
import com.syncro.domain.usecase.SaveNoteUseCase
import com.syncro.domain.usecase.ObserveSyncStateUseCase
import com.syncro.domain.model.SyncState
import com.syncro.domain.model.DayMark
import com.syncro.domain.model.HomeSavings
import com.syncro.domain.usecase.ObserveHomeSavingsUseCase
import com.syncro.domain.model.dayMarks
import com.syncro.domain.usecase.GetEventsInRangeUseCase
import com.syncro.domain.usecase.GetTasksInRangeUseCase
import com.syncro.domain.usecase.UndoDeleteEventUseCase
import com.syncro.domain.usecase.UndoDeleteNoteUseCase
import com.syncro.domain.usecase.UndoDeleteTaskUseCase
import com.syncro.presentation.home.components.weekStripDates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class HomeUiState(
    /** Hoy según el reloj inyectado; cambia a medianoche aunque la app siga abierta. */
    val today: LocalDate,
    val selectedDate: LocalDate = today,
    /** Nombre de pila del usuario; vacío si Google no lo proporciona. */
    val userName: String = "",
    val userPhotoUrl: String? = null,
    /** La frase del día; null si no toca (apagada en Ajustes o cerrada hoy). */
    val quote: Quote? = null,
    /** Todo lo del día seleccionado, en orden `sortedForDay`. */
    val timelineItems: List<SyncroItem> = emptyList(),
    /** Las prioridades elegidas para el día seleccionado (en el orden en que se eligieron). */
    val focusTasks: List<SyncroItem.Task> = emptyList(),
    val notes: List<SyncroItem.Note> = emptyList(),
    val isLoading: Boolean = false,
    val syncMessage: String? = null,
    /** El aviso bajo la cabecera cuando algo no está sincronizado con Google; null si todo está al día. */
    val syncNotice: SyncNotice? = null,
    /** Los días de la tira de la semana con tareas o eventos (sin entrada = día vacío). */
    val dayMarks: Map<LocalDate, DayMark> = emptyMap(),
    /** El resumen de Ahorros del mes; null si no se ha activado en Ajustes. */
    val savings: HomeSavings? = null
)

/** Por qué lo que se ve en Inicio puede no coincidir con Google. */
sealed interface SyncNotice {
    /** Sin conexión: se ve lo guardado en el móvil y [pendingChanges] cambios esperan a subirse. */
    data class Offline(val pendingChanges: Int) : SyncNotice

    /** Hay conexión pero la última sincronización falló (no por permisos: esos se piden aparte). */
    data object SyncFailed : SyncNotice

    /** Hay conexión pero [count] cambios siguen sin subir pasado un rato. */
    data class PendingChanges(val count: Int) : SyncNotice
}

/**
 * Qué aviso toca: sin conexión manda (es la causa de lo demás); después un fallo de la última
 * sincronización y por último los cambios que no se han podido subir.
 */
internal fun syncNoticeFor(state: SyncState, lastSyncFailed: Boolean): SyncNotice? = when {
    !state.isOnline -> SyncNotice.Offline(state.pendingChanges)
    lastSyncFailed -> SyncNotice.SyncFailed
    state.pendingChanges > 0 -> SyncNotice.PendingChanges(state.pendingChanges)
    else -> null
}

sealed class HomeEffect {
    data class LaunchAuthRecovery(val intent: Intent) : HomeEffect()
    data class ShowSnackbar(val message: String) : HomeEffect()

    /**
     * Aviso con "Deshacer". La pantalla llama a [HomeViewModel.undo] si se pulsa y a
     * [HomeViewModel.undoExpired] si no (también si se cierra la pantalla antes).
     */
    data class OfferUndo(val message: String, val undo: HomeUndo) : HomeEffect()
}

/** Lo que se puede deshacer desde el aviso. */
sealed interface HomeUndo {
    data class DeletedTask(val taskId: String) : HomeUndo
    data class DeletedEvent(val eventId: String) : HomeUndo
    data class DeletedNote(val note: SyncroItem.Note) : HomeUndo
    data class CompletedTask(val taskId: String) : HomeUndo
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getTimelineUseCase: GetTimelineUseCase,
    private val saveTaskUseCase: SaveTaskUseCase,
    private val saveEventUseCase: SaveEventUseCase,
    private val toggleTaskCompletionUseCase: ToggleTaskCompletionUseCase,
    private val toggleSubtaskCompletionUseCase: ToggleSubtaskCompletionUseCase,
    private val toggleEventCompletionUseCase: ToggleEventCompletionUseCase,
    private val deleteEventUseCase: DeleteEventUseCase,
    private val deleteTaskUseCase: DeleteTaskUseCase,
    private val syncGoogleTasksUseCase: SyncGoogleTasksUseCase,
    private val syncGoogleCalendarUseCase: SyncGoogleCalendarUseCase,
    private val pushPendingChangesUseCase: com.syncro.domain.usecase.PushPendingChangesUseCase,
    getNotesUseCase: GetNotesUseCase,
    getLocalUserUseCase: GetLocalUserUseCase,
    observeDailyQuoteUseCase: ObserveDailyQuoteUseCase,
    private val hideDailyQuoteUseCase: HideDailyQuoteUseCase,
    private val saveNoteUseCase: SaveNoteUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase,
    getDailyFocusUseCase: GetDailyFocusUseCase,
    observeSyncStateUseCase: ObserveSyncStateUseCase,
    private val clock: Clock,
    getTasksInRangeUseCase: GetTasksInRangeUseCase,
    getEventsInRangeUseCase: GetEventsInRangeUseCase,
    private val undoDeleteTaskUseCase: UndoDeleteTaskUseCase,
    private val undoDeleteEventUseCase: UndoDeleteEventUseCase,
    private val undoDeleteNoteUseCase: UndoDeleteNoteUseCase,
    observeHomeSavingsUseCase: ObserveHomeSavingsUseCase,
    private val generateRepeatsUseCase: GenerateRepeatsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(today = LocalDate.now(clock)))
    val uiState = _uiState.asStateFlow()

    private val _effect = Channel<HomeEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private var syncJob: Job? = null

    /** Si la última sincronización falló por algo que no son permisos (red, Google caído…). */
    private val lastSyncFailed = MutableStateFlow(false)

    init {
        val syncState = observeSyncStateUseCase().shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

        combine(syncState, lastSyncFailed, ::syncNoticeFor)
            .distinctUntilChanged()
            .transformLatest { notice ->
                // Cada cambio deja pendiente unos instantes mientras se sube: solo se avisa si se queda
                if (notice is SyncNotice.PendingChanges) delay(PENDING_NOTICE_DELAY_MS)
                emit(notice)
            }
            .onEach { notice -> _uiState.update { it.copy(syncNotice = notice) } }
            .launchIn(viewModelScope)

        // Al volver la conexión se sincroniza solo: sube lo pendiente y trae lo que cambió en Google
        syncState
            .map { it.isOnline }
            .distinctUntilChanged()
            .drop(1)
            .filter { it }
            .onEach { syncFromGoogle() }
            .launchIn(viewModelScope)

        observeDailyQuoteUseCase()
            .onEach { quote -> _uiState.update { it.copy(quote = quote) } }
            .launchIn(viewModelScope)

        // Observar cambios en la fecha seleccionada
        _uiState
            .map { it.selectedDate }
            .distinctUntilChanged()
            .onEach { date ->
                // Cada vez que cambia el día, lanzamos la sincronización
                syncFromGoogle(date, force = false)
            }
            .flatMapLatest { date ->
                // Observamos la base de datos para ese día
                getTimelineUseCase(date)
            }
            .onEach { items ->
                _uiState.update { it.copy(timelineItems = items) }
            }
            .launchIn(viewModelScope)

        // Repeticiones de tareas y eventos: al abrir y cada día nuevo se crean las que falten por delante
        _uiState
            .map { it.today }
            .distinctUntilChanged()
            .onEach { generateRepeatsUseCase() }
            .launchIn(viewModelScope)

        // Ahorros del mes en curso (cambia el día 1, o el de nómina, aunque la app siga abierta)
        _uiState
            .map { it.today }
            .distinctUntilChanged()
            .flatMapLatest { today -> observeHomeSavingsUseCase(today) }
            .onEach { savings -> _uiState.update { it.copy(savings = savings) } }
            .launchIn(viewModelScope)

        // Puntos de la tira de la semana: las mismas semanas que muestra, rehechas al cambiar de día
        _uiState
            .map { it.today }
            .distinctUntilChanged()
            .flatMapLatest { today ->
                val dates = weekStripDates(today)
                val from = dates.first()
                val to = dates.last()
                combine(getTasksInRangeUseCase(from, to), getEventsInRangeUseCase(from, to)) { tasks, events ->
                    dayMarks(tasks, events, from, to)
                }
            }
            .onEach { marks -> _uiState.update { it.copy(dayMarks = marks) } }
            .launchIn(viewModelScope)

        _uiState
            .map { it.selectedDate }
            .distinctUntilChanged()
            .flatMapLatest { date -> getDailyFocusUseCase(date) }
            .onEach { focus -> _uiState.update { it.copy(focusTasks = focus) } }
            .launchIn(viewModelScope)

        getNotesUseCase()
            .onEach { notes ->
                _uiState.update { it.copy(notes = notes) }
            }
            .launchIn(viewModelScope)

        getLocalUserUseCase()
            .onEach { user ->
                _uiState.update {
                    it.copy(userName = user?.name.orEmpty().trim().substringBefore(' '), userPhotoUrl = user?.photoUrl)
                }
            }
            .launchIn(viewModelScope)
    }

    fun syncFromGoogle(date: LocalDate = _uiState.value.selectedDate, force: Boolean = true) {
        syncJob?.cancel()
        isAuthRecoveryInProgress = false
        syncJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // 1. Primero subimos los cambios locales pendientes (de cualquier día) para que la descarga no los pise
                pushPendingChangesUseCase()

                // 2. Sincronizamos Calendar y Tasks en paralelo
                val tasksDeferred = async { syncGoogleTasksUseCase(force) }
                val calendarDeferred = async { syncGoogleCalendarUseCase(date) }

                val tasksResult = tasksDeferred.await()
                val calendarResult = calendarDeferred.await()

                tasksResult.onFailure { handleSyncError(it) }
                calendarResult.onFailure { handleSyncError(it) }
                // Los permisos se piden con su propia pantalla: no cuentan como fallo para el aviso
                lastSyncFailed.value = listOf(tasksResult, calendarResult).any { result ->
                    result.exceptionOrNull()?.let { it !is UserRecoverableAuthIOException } == true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error en la sincronización", e)
                lastSyncFailed.value = true
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private var isAuthRecoveryInProgress = false

    private fun handleSyncError(throwable: Throwable) {
        if (throwable is UserRecoverableAuthIOException) {
            if (!isAuthRecoveryInProgress) {
                isAuthRecoveryInProgress = true
                viewModelScope.launch {
                    _effect.send(HomeEffect.LaunchAuthRecovery(throwable.intent))
                }
            }
        } else {
            Log.e("HomeViewModel", "Error de sincronización", throwable)
        }
    }

    /** La ✕ de la frase del día: no se vuelve a ver hasta mañana. */
    fun hideDailyQuote() {
        viewModelScope.launch { hideDailyQuoteUseCase() }
    }

    fun onDaySelected(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    /** El botón "Hoy" de la cabecera. */
    fun goToToday() {
        refreshToday()
        onDaySelected(_uiState.value.today)
    }

    /**
     * La pantalla lo llama cada minuto y al volver a la app. Si ha pasado la medianoche, "hoy"
     * avanza; quien estaba mirando hoy pasa al nuevo hoy, y quien miraba otro día se queda en él.
     */
    fun refreshToday() {
        val today = LocalDate.now(clock)
        _uiState.update { state ->
            if (state.today == today) {
                state
            } else {
                state.copy(
                    today = today,
                    selectedDate = if (state.selectedDate == state.today) today else state.selectedDate
                )
            }
        }
    }

    /** Las tareas solo tienen día: se guardan a las 00:00 ("todo el día"), como las de Google Tasks. */
    fun saveQuickTask(
        title: String,
        description: String,
        date: LocalDate
    ) {
        viewModelScope.launch {
            val result = saveTaskUseCase(
                title = title,
                description = description,
                date = date,
                time = LocalTime.MIDNIGHT
            )
            val msg = result.fold(
                onSuccess = { "Tarea creada correctamente" },
                onFailure = { it.message ?: "No se pudo guardar la tarea" }
            )
            _effect.send(HomeEffect.ShowSnackbar(msg))
        }
    }

    fun saveDetailedEvent(
        id: String? = null,
        title: String,
        description: String?,
        location: String?,
        date: LocalDate,
        endDate: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
        categoryText: String,
        categoryColor: Color,
        priority: Priority?,
        subtasks: List<String>,
        repeat: Recurrence? = null,
        scope: RepeatScope = RepeatScope.THIS,
        reminderMinutes: Int? = null
    ) {
        viewModelScope.launch {
            val result = saveEventUseCase(
                id = id,
                title = title,
                description = description,
                location = location,
                date = date,
                endDate = endDate,
                startTime = startTime,
                endTime = endTime,
                categoryText = categoryText,
                categoryColor = categoryColor.toArgbColor(),
                priority = priority,
                subtasks = subtasks,
                repeat = repeat,
                scope = scope,
                reminderMinutes = reminderMinutes
            )
            val msg = result.fold(
                onSuccess = { if (id == null) "Evento creado correctamente" else "Evento actualizado correctamente" },
                onFailure = { it.message ?: "No se pudo guardar el evento" }
            )
            _effect.send(HomeEffect.ShowSnackbar(msg))
        }
    }

    fun toggleTaskCompletion(taskId: String) {
        val wasCompleted = _uiState.value.let { state ->
            (state.timelineItems + state.focusTasks).filterIsInstance<SyncroItem.Task>().firstOrNull { it.id == taskId }?.isCompleted
        }
        viewModelScope.launch {
            toggleTaskCompletionUseCase(taskId)
            // Al completarla salta al final de la lista: se ofrece deshacer por si fue sin querer
            if (wasCompleted == false) _effect.send(HomeEffect.OfferUndo("Tarea completada", HomeUndo.CompletedTask(taskId)))
        }
    }

    fun toggleEventCompletion(eventId: String) {
        viewModelScope.launch {
            toggleEventCompletionUseCase(eventId)
        }
    }

    /** Se quita al momento pero no se borra en Google hasta que pasa el aviso de "Deshacer". */
    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            deleteEventUseCase(eventId, uploadNow = false)
            _effect.send(HomeEffect.OfferUndo("Evento eliminado", HomeUndo.DeletedEvent(eventId)))
        }
    }

    /** Ver [deleteEvent]. */
    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            deleteTaskUseCase(taskId, uploadNow = false)
            _effect.send(HomeEffect.OfferUndo("Tarea eliminada", HomeUndo.DeletedTask(taskId)))
        }
    }

    /** Un evento que se repite y los siguientes. Son varios: se borran sin "Deshacer". */
    fun deleteEventAndFollowing(eventId: String) {
        viewModelScope.launch {
            deleteEventUseCase(eventId, scope = RepeatScope.THIS_AND_FOLLOWING)
            _effect.send(HomeEffect.ShowSnackbar("Eliminados este evento y los siguientes"))
        }
    }

    /** Ver [deleteEventAndFollowing]. */
    fun deleteTaskAndFollowing(taskId: String) {
        viewModelScope.launch {
            deleteTaskUseCase(taskId, scope = RepeatScope.THIS_AND_FOLLOWING)
            _effect.send(HomeEffect.ShowSnackbar("Eliminadas esta tarea y las siguientes"))
        }
    }

    /** "Deshacer" pulsado. */
    fun undo(action: HomeUndo) {
        viewModelScope.launch {
            val undone = when (action) {
                is HomeUndo.DeletedTask -> undoDeleteTaskUseCase(action.taskId)
                is HomeUndo.DeletedEvent -> undoDeleteEventUseCase(action.eventId)
                is HomeUndo.DeletedNote -> {
                    undoDeleteNoteUseCase(action.note)
                    true
                }
                is HomeUndo.CompletedTask -> {
                    toggleTaskCompletionUseCase(action.taskId)
                    true
                }
            }
            // Otra sincronización subió el borrado antes de pulsar: ya está borrado en Google
            if (!undone) _effect.send(HomeEffect.ShowSnackbar("Ya se había borrado en Google"))
        }
    }

    /** El aviso pasó sin deshacer: ahora sí se sube el borrado a Google. */
    fun undoExpired(action: HomeUndo) {
        if (action is HomeUndo.DeletedTask || action is HomeUndo.DeletedEvent) {
            viewModelScope.launch { pushPendingChangesUseCase() }
        }
    }

    fun toggleSubtaskCompletion(eventId: String, subtaskTitle: String) {
        viewModelScope.launch {
            toggleSubtaskCompletionUseCase(eventId, subtaskTitle)
        }
    }

    fun saveNote(id: String? = null, title: String, content: String, color: Color) {
        viewModelScope.launch {
            val result = saveNoteUseCase(id = id, title = title, content = content, color = color.toArgbColor())
            val msg = result.fold(
                onSuccess = { if (id == null) "Nota guardada" else "Nota actualizada" },
                onFailure = { it.message ?: "No se pudo guardar la nota" }
            )
            _effect.send(HomeEffect.ShowSnackbar(msg))
        }
    }

    fun deleteNote(note: SyncroItem.Note) {
        viewModelScope.launch {
            deleteNoteUseCase(note.id)
            _effect.send(HomeEffect.OfferUndo("Nota eliminada", HomeUndo.DeletedNote(note)))
        }
    }

    private companion object {
        /**
         * Lo que tarda en avisar de cambios sin subir: más que una subida normal y que el aviso de
         * "Deshacer" (mientras se ofrece, el borrado está pendiente a propósito).
         */
        const val PENDING_NOTICE_DELAY_MS = 10_000L
    }
}
