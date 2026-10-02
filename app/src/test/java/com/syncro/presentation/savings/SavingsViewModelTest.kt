package com.syncro.presentation.savings

import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.MovementType
import com.syncro.domain.usecase.DeleteMovementUseCase
import com.syncro.domain.usecase.GetMonthMovementsUseCase
import com.syncro.domain.usecase.SaveMovementUseCase
import com.syncro.testutil.FakeMovementRepository
import com.syncro.testutil.MainDispatcherRule
import com.syncro.testutil.aMovement
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * Plan: el ViewModel de Ahorros muestra el mes actual (según el reloj), cambia de mes, guarda y
 * borra. Riesgos: abrir en un mes que no es el de hoy y, al apuntar algo de otro mes, no verlo.
 */
class SavingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 10, 2)
    private val clock = Clock.fixed(today.atTime(9, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val repository = FakeMovementRepository()

    private fun createViewModel() = SavingsViewModel(
        getMonthMovementsUseCase = GetMonthMovementsUseCase(repository),
        saveMovementUseCase = SaveMovementUseCase(repository),
        deleteMovementUseCase = DeleteMovementUseCase(repository),
        clock = clock
    )

    private fun TestScope.collectMessages(viewModel: SavingsViewModel): List<String> {
        val messages = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.messages.toList(messages) }
        return messages
    }

    private fun TestScope.observeState(viewModel: SavingsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
    }

    @Test
    fun `abre en el mes de hoy con sus totales`() = runTest {
        repository.insertMovement(aMovement(id = "nomina", type = MovementType.INCOME, amountCents = 200_000, category = MovementCategory.SALARY, date = LocalDate.of(2026, 9, 30), repeatsMonthly = true))
        val viewModel = createViewModel()
        observeState(viewModel)

        assertEquals(YearMonth.of(2026, 10), viewModel.currentMonth.value)
        assertEquals(200_000L, viewModel.state.value!!.incomeCents)
    }

    @Test
    fun `cambiar de mes muestra los movimientos de ese mes`() = runTest {
        repository.insertMovement(aMovement(id = "septiembre", date = LocalDate.of(2026, 9, 10)))
        val viewModel = createViewModel()
        observeState(viewModel)

        assertTrue(viewModel.state.value!!.occurrences.isEmpty())
        viewModel.previousMonth()

        assertEquals(listOf("septiembre"), viewModel.state.value!!.occurrences.map { it.movement.id })
    }

    @Test
    fun `guardar confirma y lleva al mes del movimiento`() = runTest {
        val viewModel = createViewModel()
        observeState(viewModel)
        val messages = collectMessages(viewModel)

        viewModel.save(MovementType.EXPENSE, 4_590, MovementCategory.GROCERIES, LocalDate.of(2026, 9, 29), "", false)

        assertEquals(listOf("Gasto guardado"), messages)
        assertEquals(YearMonth.of(2026, 9), viewModel.currentMonth.value)
        assertEquals(4_590L, viewModel.state.value!!.expenseCents)
    }

    @Test
    fun `un importe no valido avisa y no guarda`() = runTest {
        val viewModel = createViewModel()
        val messages = collectMessages(viewModel)

        viewModel.save(MovementType.INCOME, 0, MovementCategory.SALARY, today, "", false)

        assertEquals(listOf("Introduce un importe mayor que 0"), messages)
        assertTrue(repository.movements.value.isEmpty())
    }

    @Test
    fun `borrar quita el movimiento`() = runTest {
        repository.insertMovement(aMovement(id = "m1", date = today))
        val viewModel = createViewModel()

        viewModel.delete("m1")

        assertTrue(repository.movements.value.isEmpty())
    }
}
