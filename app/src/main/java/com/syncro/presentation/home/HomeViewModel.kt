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
import com.syncro.domain.usecase.ToggleTaskCompletionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val syncGoogleTasksUseCase: SyncGoogleTasksUseCase,
    private val syncGoogleCalendarUseCase: SyncGoogleCalendarUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    private val _effect = Channel<HomeEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        // Observar tareas y eventos del día seleccionado
        _uiState
            .map { it.selectedDate }
            .distinctUntilChanged()
            .flatMapLatest { date ->
                getTimelineUseCase(date)
            }
            .onEach { items ->
                Log.d("HomeViewModel", "Timeline updated: ${items.size} items for date ${_uiState.value.selectedDate}")
                items.forEach { Log.d("HomeViewModel", "Item: ${it.javaClass.simpleName} - Title: ${if (it is SyncroItem.Event) it.title else (it as SyncroItem.Task).title}") }
                _uiState.update { it.copy(timelineItems = items) }
            }
            .launchIn(viewModelScope)
            
        // Sincronización inicial
        syncFromGoogle()
    }

    fun syncFromGoogle() {
        isAuthRecoveryInProgress = false
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val tasksResult = syncGoogleTasksUseCase()
                val calendarResult = syncGoogleCalendarUseCase()

                tasksResult.onFailure { handleSyncError(it) }
                calendarResult.onFailure { handleSyncError(it) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private var isAuthRecoveryInProgress = false

    private fun handleSyncError(throwable: Throwable) {
        Log.d("HomeViewModel", "Handling sync error: ${throwable.javaClass.simpleName}")
        if (throwable is UserRecoverableAuthIOException) {
            if (!isAuthRecoveryInProgress) {
                isAuthRecoveryInProgress = true
                Log.d("HomeViewModel", "UserRecoverableAuthIOException detected, sending effect")
                viewModelScope.launch {
                    _effect.send(HomeEffect.LaunchAuthRecovery(throwable.intent))
                }
            } else {
                Log.d("HomeViewModel", "Auth recovery already in progress, skipping duplicate effect")
            }
        } else {
            Log.e("HomeViewModel", "Non-recoverable sync error", throwable)
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

    fun toggleSubtaskCompletion(eventId: String, subtaskTitle: String) {
        // TODO: Implementar cuando los eventos estén en Room
    }
}
