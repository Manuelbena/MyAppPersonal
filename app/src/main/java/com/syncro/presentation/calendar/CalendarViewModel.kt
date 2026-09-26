package com.syncro.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.usecase.GetEventsInRangeUseCase
import com.syncro.domain.usecase.GetTasksInRangeUseCase
import com.syncro.domain.usecase.SyncGoogleCalendarUseCase
import com.syncro.domain.usecase.SyncGoogleTasksUseCase
import com.syncro.domain.usecase.ToggleEventCompletionUseCase
import com.syncro.domain.usecase.ToggleSubtaskCompletionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class CalendarUiState(
    val events: Map<LocalDate, List<SyncroItem>> = emptyMap(),
    val isLoading: Boolean = false,
    val selectedMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val getEventsInRangeUseCase: GetEventsInRangeUseCase,
    private val getTasksInRangeUseCase: GetTasksInRangeUseCase,
    private val syncGoogleCalendarUseCase: SyncGoogleCalendarUseCase,
    private val syncGoogleTasksUseCase: SyncGoogleTasksUseCase,
    private val toggleEventCompletionUseCase: ToggleEventCompletionUseCase,
    private val toggleSubtaskCompletionUseCase: ToggleSubtaskCompletionUseCase
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    private val _isLoading = MutableStateFlow(false)
    
    val uiState: StateFlow<CalendarUiState> = combine(_selectedMonth, _selectedDate, _isLoading) { month, selectedDate, isLoading ->
        Triple(month, selectedDate, isLoading)
    }.flatMapLatest { (month, selectedDate, isLoading) ->
        val start = month.atDay(1)
        val end = month.atEndOfMonth()
        
        combine(
            getEventsInRangeUseCase(start, end),
            getTasksInRangeUseCase(start, end)
        ) { events, tasks ->
            // Un evento de varios días aparece en cada uno de ellos (21:30 → 01:00 en ambos días)
            val eventsByDay = events.flatMap { event -> event.days.map { day -> day to event } }
            val tasksByDay = tasks.map { it.date to it }
            CalendarUiState(
                events = (eventsByDay + tasksByDay).groupBy({ it.first }, { it.second }),
                selectedMonth = month,
                selectedDate = selectedDate,
                isLoading = isLoading
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarUiState())

    init {
        // Carga inicial
        syncMonth(_selectedMonth.value)
    }

    fun onMonthChanged(month: YearMonth) {
        if (_selectedMonth.value != month) {
            _selectedMonth.value = month
            syncMonth(month)
        }
    }

    fun onDateSelected(date: LocalDate?) {
        _selectedDate.value = date
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

    private var syncJob: Job? = null

    private fun syncMonth(month: YearMonth) {
        // Al cambiar rápido de mes solo interesa la última sincronización
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                val calendarDeferred = async { syncGoogleCalendarUseCase(month.atDay(1), month.atEndOfMonth()) }
                val tasksDeferred = async { syncGoogleTasksUseCase() }
                
                calendarDeferred.await()
                tasksDeferred.await()
            } catch (e: Exception) {
                // Manejar error de red, se mantienen los datos locales
            } finally {
                // Si se canceló por un cambio de mes, el indicador lo gestiona la sync nueva
                if (isActive) _isLoading.value = false
            }
        }
    }
}
