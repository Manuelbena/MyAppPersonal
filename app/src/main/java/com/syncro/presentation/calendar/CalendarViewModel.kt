package com.syncro.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.usecase.GetEventsInRangeUseCase
import com.syncro.domain.usecase.SyncGoogleCalendarUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class CalendarUiState(
    val events: Map<LocalDate, List<SyncroItem.Event>> = emptyMap(),
    val isLoading: Boolean = false,
    val selectedMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val getEventsInRangeUseCase: GetEventsInRangeUseCase,
    private val syncGoogleCalendarUseCase: SyncGoogleCalendarUseCase
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    
    val uiState: StateFlow<CalendarUiState> = combine(_selectedMonth, _selectedDate) { month, selectedDate ->
        month to selectedDate
    }.flatMapLatest { (month, selectedDate) ->
        val start = month.atDay(1)
        val end = month.atEndOfMonth()
        getEventsInRangeUseCase(start, end).map { events ->
            CalendarUiState(
                events = events.groupBy { it.date },
                selectedMonth = month,
                selectedDate = selectedDate
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarUiState())

    fun onMonthChanged(month: YearMonth) {
        _selectedMonth.value = month
        syncMonth(month)
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
            // Sincronizar algunos días del mes o el mes entero
            // Por simplicidad, sincronizamos el primer día del mes
            syncGoogleCalendarUseCase(month.atDay(1))
        }
    }
}
