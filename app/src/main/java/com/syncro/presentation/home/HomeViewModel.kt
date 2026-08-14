package com.syncro.presentation.home

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.usecase.GetTimelineUseCase
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
    val isLoading: Boolean = false
)

sealed class HomeEffect {
    data class LaunchAuthRecovery(val intent: Intent) : HomeEffect()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getTimelineUseCase: GetTimelineUseCase,
    private val saveTaskUseCase: SaveTaskUseCase,
    private val toggleTaskCompletionUseCase: ToggleTaskCompletionUseCase,
    private val toggleSubtaskCompletionUseCase: ToggleSubtaskCompletionUseCase,
    private val toggleEventCompletionUseCase: ToggleEventCompletionUseCase,
    private val syncGoogleTasksUseCase: SyncGoogleTasksUseCase,
    private val syncGoogleCalendarUseCase: SyncGoogleCalendarUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    private val _effect = Channel<HomeEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private var syncJob: Job? = null

    init {
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
                // Sincronizamos Calendar y Tasks en paralelo
                val tasksDeferred = async { syncGoogleTasksUseCase(date) }
                val calendarDeferred = async { syncGoogleCalendarUseCase(date) }

                tasksDeferred.await().onFailure { handleSyncError(it) }
                calendarDeferred.await().onFailure { handleSyncError(it) }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error en la sincronización", e)
            } finally {
                // Solo quitamos el cargando cuando ambas peticiones han terminado y guardado en BBDD
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

    fun saveQuickTask(title: String, description: String, date: LocalDate, time: LocalTime) {
        viewModelScope.launch {
            val timeString = time.format(DateTimeFormatter.ofPattern("HH:mm"))
            saveTaskUseCase(title, description, date, timeString)
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
