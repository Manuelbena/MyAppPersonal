package com.syncro.presentation.home

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
    private val uploadUnsyncedItemsUseCase: com.syncro.domain.usecase.UploadUnsyncedItemsUseCase
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
                syncFromGoogle(date)
            }
            .flatMapLatest { date ->
                // Observamos la base de datos para ese día
                getTimelineUseCase(date)
            }
            .onEach { items ->
                _uiState.update { it.copy(timelineItems = items) }
            }
            .launchIn(viewModelScope)
    }

    fun syncFromGoogle(date: LocalDate = _uiState.value.selectedDate) {
        syncJob?.cancel()
        isAuthRecoveryInProgress = false
        syncJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // 1. Primero subimos lo que esté en local y no en Google
                uploadUnsyncedItemsUseCase(date)

                // 2. Sincronizamos Calendar y Tasks en paralelo
                val tasksDeferred = async { syncGoogleTasksUseCase(date) }
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
        time: LocalTime,
        categoryText: String? = null,
        categoryColor: Color? = null
    ) {
        viewModelScope.launch {
            val timeString = time.format(DateTimeFormatter.ofPattern("HH:mm"))
            saveTaskUseCase(
                title = title, 
                description = description, 
                date = date, 
                time = timeString,
                categoryText = categoryText,
                categoryColor = categoryColor
            )
            _effect.send(HomeEffect.ShowSnackbar("Tarea creada correctamente"))
        }
    }

    fun saveDetailedEvent(
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
            saveEventUseCase(
                title = title,
                description = description,
                location = location,
                date = date,
                startTime = startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                endTime = endTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                categoryText = categoryText,
                categoryColor = categoryColor,
                priority = priority,
                subtasks = subtasks
            )
            _effect.send(HomeEffect.ShowSnackbar("Evento creado correctamente"))
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
}
