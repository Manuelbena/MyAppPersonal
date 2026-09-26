package com.syncro.presentation.home

import com.syncro.presentation.theme.toArgbColor
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import androidx.compose.ui.graphics.Color
import com.syncro.domain.model.Priority
import com.syncro.domain.model.QuotesProvider
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.usecase.GetTimelineUseCase
import com.syncro.domain.usecase.SaveEventUseCase
import com.syncro.domain.usecase.SaveTaskUseCase
import com.syncro.domain.usecase.SyncGoogleCalendarUseCase
import com.syncro.domain.usecase.SyncGoogleTasksUseCase
import com.syncro.domain.usecase.ToggleEventCompletionUseCase
import com.syncro.domain.usecase.ToggleSubtaskCompletionUseCase
import com.syncro.domain.usecase.ToggleTaskCompletionUseCase
import com.syncro.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class HomeUiState(
    val userName: String = "Manuel",
    val selectedDate: LocalDate = LocalDate.now(),
    val quote: String = "La mejor manera de empezar es dejar de hablar y empezar a hacer.",
    val quoteAuthor: String = "Walt Disney",
    val timelineItems: List<SyncroItem> = emptyList(),
    val notes: List<SyncroItem.Note> = emptyList(),
    val isLoading: Boolean = false,
    val syncMessage: String? = null
)

sealed class HomeEffect {
    data class LaunchAuthRecovery(val intent: Intent) : HomeEffect()
    data class ShowSnackbar(val message: String) : HomeEffect()
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
    private val syncGoogleTasksUseCase: SyncGoogleTasksUseCase,
    private val syncGoogleCalendarUseCase: SyncGoogleCalendarUseCase,
    private val pushPendingChangesUseCase: com.syncro.domain.usecase.PushPendingChangesUseCase,
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    private val _effect = Channel<HomeEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private var syncJob: Job? = null

    init {
        // Frase del día basada en el día del año
        val dayOfYear = LocalDate.now().dayOfYear
        val quoteIndex = dayOfYear % QuotesProvider.quotes.size
        val dailyQuote = QuotesProvider.quotes[quoteIndex]
        _uiState.update { it.copy(quote = dailyQuote.text, quoteAuthor = dailyQuote.author) }

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

        noteRepository.getAllNotes()
            .onEach { notes ->
                _uiState.update { it.copy(notes = notes) }
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

                if (!tasksResult.isSuccess || !calendarResult.isSuccess) {
                    tasksResult.onFailure { handleSyncError(it) }
                    calendarResult.onFailure { handleSyncError(it) }
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error en la sincronización", e)
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

    fun onDaySelected(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun saveQuickTask(
        title: String, 
        description: String, 
        date: LocalDate, 
        time: LocalTime
    ) {
        viewModelScope.launch {
            val timeString = time.format(DateTimeFormatter.ofPattern("HH:mm"))
            val result = saveTaskUseCase(
                title = title, 
                description = description, 
                date = date, 
                time = timeString
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
        startTime: LocalTime,
        endTime: LocalTime,
        categoryText: String,
        categoryColor: Color,
        priority: Priority?,
        subtasks: List<String>
    ) {
        viewModelScope.launch {
            val result = saveEventUseCase(
                id = id,
                title = title,
                description = description,
                location = location,
                date = date,
                startTime = startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                endTime = endTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                categoryText = categoryText,
                categoryColor = categoryColor.toArgbColor(),
                priority = priority,
                subtasks = subtasks
            )
            val msg = result.fold(
                onSuccess = { if (id == null) "Evento creado correctamente" else "Evento actualizado correctamente" },
                onFailure = { it.message ?: "No se pudo guardar el evento" }
            )
            _effect.send(HomeEffect.ShowSnackbar(msg))
        }
    }

    fun toggleTaskCompletion(taskId: String) {
        viewModelScope.launch {
            toggleTaskCompletionUseCase(taskId)
        }
    }

    fun toggleEventCompletion(eventId: String) {
        viewModelScope.launch {
            toggleEventCompletionUseCase(eventId)
        }
    }

    fun toggleSubtaskCompletion(eventId: String, subtaskTitle: String) {
        viewModelScope.launch {
            toggleSubtaskCompletionUseCase(eventId, subtaskTitle)
        }
    }

    fun saveNote(id: String? = null, title: String, content: String, color: Color) {
        viewModelScope.launch {
            val note = SyncroItem.Note(
                id = id ?: java.util.UUID.randomUUID().toString(),
                title = title,
                content = content,
                color = color.toArgbColor(),
                createdAt = java.time.LocalDateTime.now()
            )
            noteRepository.insertNote(note)
            _effect.send(HomeEffect.ShowSnackbar(if (id == null) "Nota guardada" else "Nota actualizada"))
        }
    }

    fun deleteNote(note: SyncroItem.Note) {
        viewModelScope.launch {
            noteRepository.deleteNote(note)
            _effect.send(HomeEffect.ShowSnackbar("Nota eliminada"))
        }
    }
}
