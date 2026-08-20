package com.syncro.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.usecase.GetEventsInRangeUseCase
import com.syncro.domain.usecase.GetTasksInRangeUseCase
import com.syncro.domain.usecase.SyncGoogleCalendarUseCase
import com.syncro.domain.usecase.SyncGoogleTasksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val syncGoogleTasksUseCase: SyncGoogleTasksUseCase
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
            val allItems = events + tasks
            CalendarUiState(
                events = allItems.groupBy { item ->
                    when (item) {
                        is SyncroItem.Event -> item.date
                        is SyncroItem.Task -> item.date
                        is SyncroItem.Note -> item.createdAt.toLocalDate()
                    }
                },
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
        // TODO: Implement toggle in use case
    }

    fun toggleSubtaskCompletion(eventId: String, subtaskTitle: String) {
        // TODO: Implement toggle in use case
    }

    private fun syncMonth(month: YearMonth) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Sincronizamos el mes cargando día a día o mediante una estrategia de rango si estuviera disponible.
                // Por ahora, sincronizamos al menos el inicio del mes para disparar la carga de Google.
                val calendarDeferred = async { syncGoogleCalendarUseCase(month.atDay(1)) }
                val tasksDeferred = async { syncGoogleTasksUseCase(month.atDay(1)) }
                
                calendarDeferred.await()
                tasksDeferred.await()
            } catch (e: Exception) {
                // Manejar error de red, se mantienen los datos locales
            } finally {
                _isLoading.value = false
            }
        }
    }
}
