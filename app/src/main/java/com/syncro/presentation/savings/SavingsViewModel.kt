package com.syncro.presentation.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syncro.domain.model.ArgbColor
import com.syncro.domain.model.Budget
import com.syncro.domain.model.MAIN_ACCOUNT_ID
import com.syncro.domain.model.MonthMovements
import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.MovementType
import com.syncro.domain.model.SavingsAccounts
import com.syncro.domain.model.SavingsPeriod
import com.syncro.domain.usecase.DeleteBudgetUseCase
import com.syncro.domain.usecase.DeleteMovementUseCase
import com.syncro.domain.usecase.DeleteSavingsAccountUseCase
import com.syncro.domain.usecase.GetBudgetsUseCase
import com.syncro.domain.usecase.GetMonthMovementsUseCase
import com.syncro.domain.usecase.ObserveSavingsAccountsUseCase
import com.syncro.domain.usecase.SaveBudgetUseCase
import com.syncro.domain.usecase.SaveMovementUseCase
import com.syncro.domain.usecase.SaveSavingsAccountUseCase
import com.syncro.domain.usecase.SelectSavingsAccountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SavingsViewModel @Inject constructor(
    getMonthMovementsUseCase: GetMonthMovementsUseCase,
    private val saveMovementUseCase: SaveMovementUseCase,
    private val deleteMovementUseCase: DeleteMovementUseCase,
    getBudgetsUseCase: GetBudgetsUseCase,
    private val saveBudgetUseCase: SaveBudgetUseCase,
    private val deleteBudgetUseCase: DeleteBudgetUseCase,
    observeAccountsUseCase: ObserveSavingsAccountsUseCase,
    private val selectAccountUseCase: SelectSavingsAccountUseCase,
    private val saveAccountUseCase: SaveSavingsAccountUseCase,
    private val deleteAccountUseCase: DeleteSavingsAccountUseCase,
    private val clock: Clock
) : ViewModel() {

    val today: LocalDate get() = LocalDate.now(clock)

    /** Las cuentas de ahorro y la que se ve; null mientras se cargan. Todo lo demás es de esa cuenta. */
    val accounts: StateFlow<SavingsAccounts?> = observeAccountsUseCase()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val activeAccountId: Flow<String> = accounts.filterNotNull().map { it.active.id }.distinctUntilChanged()

    // Un día cualquiera del mes que se ve: el mes es el periodo en que cae (de nómina a nómina si
    // hay día de nómina), así que cambiar el día de nómina en Ajustes lo recoloca solo
    private val anchor = MutableStateFlow(LocalDate.now(clock))

    /** El mes que se está viendo, de la cuenta elegida, con sus movimientos y totales; null mientras se carga. */
    val state: StateFlow<MonthMovements?> = combine(anchor, activeAccountId, ::Pair)
        .flatMapLatest { (date, accountId) -> getMonthMovementsUseCase(date, accountId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Las fechas del mes que se ve (para la cabecera); null hasta leer el día de nómina. */
    val currentPeriod: StateFlow<SavingsPeriod?> = anchor
        .flatMapLatest { getMonthMovementsUseCase.period(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** Mensajes de una vez para el snackbar ("Gasto guardado", errores). */
    val messages = _messages.receiveAsFlow()

    private val currentAccountId: String get() = accounts.value?.active?.id ?: MAIN_ACCOUNT_ID

    // El anterior acaba el día antes de que empiece este; el siguiente empieza el día después de que acabe
    fun previousMonth() {
        anchor.value = currentPeriod.value?.start?.minusDays(1) ?: anchor.value.minusMonths(1)
    }

    fun nextMonth() {
        anchor.value = currentPeriod.value?.end?.plusDays(1) ?: anchor.value.plusMonths(1)
    }

    fun save(
        type: MovementType,
        amountCents: Long,
        category: MovementCategory,
        date: LocalDate,
        note: String,
        repeatsMonthly: Boolean,
        // Con id se edita ese movimiento; sin él se crea uno nuevo
        id: String? = null
    ) {
        viewModelScope.launch {
            // Siempre en la cuenta que se ve (un movimiento que se edita es de ella)
            val result = saveMovementUseCase(type, amountCents, category, date, note, repeatsMonthly, id, currentAccountId)
            val what = if (type == MovementType.INCOME) "Ingreso" else "Gasto"
            _messages.send(
                result.fold(
                    onSuccess = { if (id == null) "$what guardado" else "$what actualizado" },
                    onFailure = { it.message ?: "No se pudo guardar" }
                )
            )
            // Se ve el mes donde ha caído lo que se acaba de apuntar
            if (result.isSuccess) anchor.value = date
        }
    }

    /** Los presupuestos por categoría de la cuenta que se ve (valen para todos los meses). */
    val budgets: StateFlow<List<Budget>> = activeAccountId
        .flatMapLatest { getBudgetsUseCase(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveBudget(category: MovementCategory, limitCents: Long) {
        viewModelScope.launch {
            _messages.send(
                saveBudgetUseCase(category, limitCents, currentAccountId).fold(
                    onSuccess = { "Presupuesto guardado" },
                    onFailure = { it.message ?: "No se pudo guardar el presupuesto" }
                )
            )
        }
    }

    fun deleteBudget(category: MovementCategory) {
        viewModelScope.launch { deleteBudgetUseCase(category, currentAccountId) }
    }

    fun delete(id: String) {
        viewModelScope.launch { deleteMovementUseCase(id) }
    }

    // region Cuentas

    fun selectAccount(id: String) {
        viewModelScope.launch { selectAccountUseCase(id) }
    }

    /** Crea una cuenta (sin [id]: pasa a verse) o cambia su nombre y color. */
    fun saveAccount(name: String, color: ArgbColor, id: String? = null) {
        viewModelScope.launch {
            _messages.send(
                saveAccountUseCase(name, color, id).fold(
                    onSuccess = { if (id == null) "Cuenta \"${it.name}\" creada" else "Cuenta actualizada" },
                    onFailure = { it.message ?: "No se pudo guardar la cuenta" }
                )
            )
        }
    }

    fun deleteAccount(id: String) {
        viewModelScope.launch {
            _messages.send(
                deleteAccountUseCase(id).fold(
                    onSuccess = { "Cuenta eliminada" },
                    onFailure = { it.message ?: "No se pudo eliminar la cuenta" }
                )
            )
        }
    }

    // endregion
}
