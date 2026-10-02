package com.syncro.presentation.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syncro.domain.model.MonthMovements
import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.MovementType
import com.syncro.domain.usecase.DeleteMovementUseCase
import com.syncro.domain.usecase.GetMonthMovementsUseCase
import com.syncro.domain.usecase.SaveMovementUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SavingsViewModel @Inject constructor(
    getMonthMovementsUseCase: GetMonthMovementsUseCase,
    private val saveMovementUseCase: SaveMovementUseCase,
    private val deleteMovementUseCase: DeleteMovementUseCase,
    private val clock: Clock
) : ViewModel() {

    val today: LocalDate get() = LocalDate.now(clock)

    private val month = MutableStateFlow(YearMonth.now(clock))

    /** El mes que se está viendo con sus movimientos y totales; null mientras se carga. */
    val state: StateFlow<MonthMovements?> = month
        .flatMapLatest { getMonthMovementsUseCase(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentMonth: StateFlow<YearMonth> = month.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** Mensajes de una vez para el snackbar ("Gasto guardado", errores). */
    val messages = _messages.receiveAsFlow()

    fun previousMonth() = month.update { it.minusMonths(1) }
    fun nextMonth() = month.update { it.plusMonths(1) }

    fun save(
        type: MovementType,
        amountCents: Long,
        category: MovementCategory,
        date: LocalDate,
        note: String,
        repeatsMonthly: Boolean
    ) {
        viewModelScope.launch {
            val result = saveMovementUseCase(type, amountCents, category, date, note, repeatsMonthly)
            _messages.send(
                result.fold(
                    onSuccess = { if (type == MovementType.INCOME) "Ingreso guardado" else "Gasto guardado" },
                    onFailure = { it.message ?: "No se pudo guardar" }
                )
            )
            // Se ve el mes donde ha caído lo que se acaba de apuntar
            if (result.isSuccess) month.value = YearMonth.from(date)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { deleteMovementUseCase(id) }
    }
}
