package com.syncro.presentation.savings

import com.syncro.domain.model.AppSettings
import com.syncro.domain.model.AssistantSettings
import com.syncro.domain.model.ArgbColor
import com.syncro.domain.model.Budget
import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.SavingsAccount
import com.syncro.domain.model.MovementType
import com.syncro.domain.model.SavingsPeriod
import com.syncro.domain.usecase.DeleteBudgetUseCase
import com.syncro.domain.usecase.DeleteMovementUseCase
import com.syncro.domain.usecase.GetBudgetsUseCase
import com.syncro.domain.usecase.SaveBudgetUseCase
import com.syncro.domain.usecase.GetMonthMovementsUseCase
import com.syncro.domain.usecase.SaveMovementUseCase
import com.syncro.testutil.FakeBudgetRepository
import com.syncro.testutil.FakeMovementRepository
import com.syncro.testutil.FakeSettingsRepository
import com.syncro.testutil.FakeSavingsAccountRepository
import com.syncro.domain.usecase.ObserveSavingsAccountsUseCase
import com.syncro.domain.usecase.SelectSavingsAccountUseCase
import com.syncro.domain.usecase.SaveSavingsAccountUseCase
import com.syncro.domain.usecase.DeleteSavingsAccountUseCase
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
    private val budgets = FakeBudgetRepository()
    private val settings = FakeSettingsRepository()
    private val accounts = FakeSavingsAccountRepository(repository, budgets)

    private fun createViewModel() = SavingsViewModel(
        getMonthMovementsUseCase = GetMonthMovementsUseCase(repository, settings),
        saveMovementUseCase = SaveMovementUseCase(repository),
        deleteMovementUseCase = DeleteMovementUseCase(repository),
        getBudgetsUseCase = GetBudgetsUseCase(budgets),
        saveBudgetUseCase = SaveBudgetUseCase(budgets),
        deleteBudgetUseCase = DeleteBudgetUseCase(budgets),
        observeAccountsUseCase = ObserveSavingsAccountsUseCase(accounts),
        selectAccountUseCase = SelectSavingsAccountUseCase(accounts),
        saveAccountUseCase = SaveSavingsAccountUseCase(accounts),
        deleteAccountUseCase = DeleteSavingsAccountUseCase(accounts),
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

        assertEquals(SavingsPeriod.of(YearMonth.of(2026, 10)), viewModel.currentPeriod.value)
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
        assertEquals(SavingsPeriod.of(YearMonth.of(2026, 9)), viewModel.currentPeriod.value)
        assertEquals(4_590L, viewModel.state.value!!.expenseCents)
    }

    @Test
    fun `editar avisa de que se ha actualizado`() = runTest {
        repository.insertMovement(aMovement(id = "m1", date = today))
        val viewModel = createViewModel()
        val messages = collectMessages(viewModel)

        viewModel.save(MovementType.EXPENSE, 2_000, MovementCategory.GROCERIES, today, "Mercadona", false, id = "m1")

        assertEquals(listOf("Gasto actualizado"), messages)
        assertEquals(2_000L, repository.movements.value.getValue("m1").amountCents)
    }

    @Test
    fun `guardar un presupuesto confirma y lo muestra`() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.budgets.collect {} }
        val messages = collectMessages(viewModel)

        viewModel.saveBudget(MovementCategory.GROCERIES, 30_000)
        viewModel.saveBudget(MovementCategory.SALARY, 30_000)

        assertEquals(listOf("Presupuesto guardado", "Los presupuestos son para gastos"), messages)
        assertEquals(listOf(MovementCategory.GROCERIES), viewModel.budgets.value.map { it.category })

        viewModel.deleteBudget(MovementCategory.GROCERIES)
        assertTrue(viewModel.budgets.value.isEmpty())
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

    private fun payday(day: Int?) {
        settings.current.value = AppSettings(assistant = AssistantSettings(paydayDay = day))
    }

    private fun period(start: LocalDate, end: LocalDate) = SavingsPeriod(start, end)

    @Test
    fun `cobrando el 27 el mes va del 27 al 26 y las flechas saltan de nomina a nomina`() = runTest {
        payday(27)
        repository.insertMovement(aMovement(id = "antes", date = LocalDate.of(2026, 9, 26)))
        repository.insertMovement(aMovement(id = "nomina", date = LocalDate.of(2026, 9, 27)))
        val viewModel = createViewModel()
        observeState(viewModel)

        // Hoy es 2 de octubre: del 27 de septiembre al 26 de octubre
        assertEquals(period(LocalDate.of(2026, 9, 27), LocalDate.of(2026, 10, 26)), viewModel.currentPeriod.value)
        assertEquals(listOf("nomina"), viewModel.state.value!!.occurrences.map { it.movement.id })

        viewModel.previousMonth()
        assertEquals(period(LocalDate.of(2026, 8, 27), LocalDate.of(2026, 9, 26)), viewModel.currentPeriod.value)
        assertEquals(listOf("antes"), viewModel.state.value!!.occurrences.map { it.movement.id })

        viewModel.nextMonth()
        viewModel.nextMonth()
        assertEquals(period(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 11, 26)), viewModel.currentPeriod.value)
    }

    @Test
    fun `cambiar el dia de nomina en Ajustes recoloca el mes que se ve`() = runTest {
        val viewModel = createViewModel()
        observeState(viewModel)
        assertEquals(SavingsPeriod.of(YearMonth.of(2026, 10)), viewModel.currentPeriod.value)

        payday(27)

        assertEquals(period(LocalDate.of(2026, 9, 27), LocalDate.of(2026, 10, 26)), viewModel.currentPeriod.value)
        assertEquals(period(LocalDate.of(2026, 9, 27), LocalDate.of(2026, 10, 26)), viewModel.state.value!!.period)
    }

    // region Cuentas de ahorro

    @Test
    fun `al cambiar de cuenta cambian los movimientos y los presupuestos`() = runTest {
        accounts.saveAccount(SavingsAccount("conjunta", "Conjunta", ArgbColor(0xFF0EA5E9)))
        repository.insertMovement(aMovement(id = "mio", date = today))
        repository.insertMovement(aMovement(id = "conjunto", date = today, accountId = "conjunta"))
        budgets.saveBudget(Budget(MovementCategory.GROCERIES, 30_000, "conjunta"))
        val viewModel = createViewModel()
        observeState(viewModel)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.budgets.collect {} }

        assertEquals(listOf("mio"), viewModel.state.value!!.occurrences.map { it.movement.id })
        assertTrue(viewModel.budgets.value.isEmpty())

        viewModel.selectAccount("conjunta")

        assertEquals(listOf("conjunto"), viewModel.state.value!!.occurrences.map { it.movement.id })
        assertEquals(listOf(MovementCategory.GROCERIES), viewModel.budgets.value.map { it.category })
    }

    @Test
    fun `lo que se apunta va a la cuenta que se ve`() = runTest {
        accounts.saveAccount(SavingsAccount("conjunta", "Conjunta", ArgbColor(0xFF0EA5E9)))
        val viewModel = createViewModel()
        observeState(viewModel)
        viewModel.selectAccount("conjunta")

        viewModel.save(MovementType.EXPENSE, 4_590, MovementCategory.GROCERIES, today, "", false)
        viewModel.saveBudget(MovementCategory.LEISURE, 10_000)

        assertEquals("conjunta", repository.movements.value.values.single().accountId)
        assertEquals("conjunta", budgets.budgets.value.values.single().accountId)
    }

    @Test
    fun `crear una cuenta la deja elegida y lo confirma`() = runTest {
        val viewModel = createViewModel()
        val messages = collectMessages(viewModel)

        viewModel.saveAccount("vacaciones", ArgbColor(0xFF0EA5E9))

        assertEquals(listOf("Cuenta \"Vacaciones\" creada"), messages)
        assertEquals("Vacaciones", viewModel.accounts.value!!.active.name)
    }

    // endregion
}
