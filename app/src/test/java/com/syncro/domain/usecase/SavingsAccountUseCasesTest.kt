package com.syncro.domain.usecase

import com.syncro.domain.model.ArgbColor
import com.syncro.domain.model.Budget
import com.syncro.domain.model.MAIN_ACCOUNT_ID
import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.SavingsPeriod
import com.syncro.domain.model.budgetAlerts
import com.syncro.domain.model.budgetStatuses
import com.syncro.domain.model.monthMovements
import com.syncro.testutil.DAY
import com.syncro.testutil.FakeBudgetRepository
import com.syncro.testutil.FakeMovementRepository
import com.syncro.testutil.FakeSavingsAccountRepository
import com.syncro.testutil.aMovement
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/**
 * Plan: cuentas de ahorro. Responsabilidades: crear (y pasar a verla), renombrar, borrar con lo
 * suyo, y que cada presupuesto mire solo los gastos de su cuenta. Riesgos: quedarse sin ninguna
 * cuenta, dos cuentas con el mismo nombre, seguir viendo una cuenta borrada, y un gasto de la
 * cuenta conjunta que gasta del presupuesto personal.
 */
class SavingsAccountUseCasesTest {

    private val movements = FakeMovementRepository()
    private val budgets = FakeBudgetRepository()
    private val accounts = FakeSavingsAccountRepository(movements, budgets)
    private val save = SaveSavingsAccountUseCase(accounts)
    private val delete = DeleteSavingsAccountUseCase(accounts)
    private val observe = ObserveSavingsAccountsUseCase(accounts)
    private val blue = ArgbColor(0xFF0EA5E9)

    @Test
    fun `una cuenta nueva se crea con su nombre limpio y pasa a ser la que se ve`() = runTest {
        val created = save("  conjunta ", blue).getOrThrow()

        val current = observe().first()
        assertEquals("Conjunta", created.name)
        assertEquals(created, current.active)
        assertEquals(listOf("Principal", "Conjunta"), current.all.map { it.name })
    }

    @Test
    fun `no se aceptan nombres vacios, muy largos o repetidos`() = runTest {
        assertTrue(save(" ", blue).isFailure)
        assertTrue(save("x".repeat(40), blue).isFailure)
        assertEquals("Ya tienes una cuenta que se llama así", save("PRINCIPAL", blue).exceptionOrNull()?.message)
        assertEquals(1, observe().first().all.size)
    }

    @Test
    fun `renombrar una cuenta no cambia la que se ve`() = runTest {
        val vacaciones = save("Vacaciones", blue).getOrThrow()
        accounts.selectAccount(MAIN_ACCOUNT_ID)

        save("Viaje a Roma", blue, id = vacaciones.id)

        val current = observe().first()
        assertEquals(MAIN_ACCOUNT_ID, current.active.id)
        assertEquals("Viaje a Roma", current.nameOf(vacaciones.id))
    }

    @Test
    fun `la unica cuenta no se puede borrar`() = runTest {
        assertEquals("Necesitas al menos una cuenta", delete(MAIN_ACCOUNT_ID).exceptionOrNull()?.message)
    }

    @Test
    fun `borrar la que se ve pasa a otra y se lleva sus movimientos y presupuestos`() = runTest {
        val conjunta = save("Conjunta", blue).getOrThrow()
        movements.insertMovement(aMovement(id = "suyo", accountId = conjunta.id))
        movements.insertMovement(aMovement(id = "principal"))
        budgets.saveBudget(Budget(MovementCategory.GROCERIES, 30_000, conjunta.id))

        assertTrue(delete(conjunta.id).isSuccess)

        assertEquals(MAIN_ACCOUNT_ID, observe().first().active.id)
        assertEquals(setOf("principal"), movements.movements.value.keys)
        assertTrue(budgets.budgets.value.isEmpty())
    }

    @Test
    fun `cada presupuesto solo cuenta los gastos de su cuenta`() {
        val month = monthMovements(
            SavingsPeriod.of(YearMonth.from(DAY)),
            listOf(
                aMovement(id = "mio", amountCents = 10_000, category = MovementCategory.GROCERIES),
                aMovement(id = "conjunto", amountCents = 25_000, category = MovementCategory.GROCERIES, accountId = "conjunta")
            )
        )
        val personal = Budget(MovementCategory.GROCERIES, 30_000)
        val shared = Budget(MovementCategory.GROCERIES, 30_000, "conjunta")

        assertEquals(listOf(25_000L, 10_000L), month.budgetStatuses(listOf(personal, shared)).map { it.spentCents })
        // El gasto conjunto (83 %) avisa en la conjunta, no en la personal
        assertEquals(listOf("conjunta"), month.budgetAlerts(listOf(personal, shared), DAY).map { it.budget.accountId })
    }

    @Test
    fun `los movimientos de un mes son los de la cuenta pedida, o de todas sin cuenta`() = runTest {
        movements.insertMovement(aMovement(id = "mio"))
        movements.insertMovement(aMovement(id = "conjunto", accountId = "conjunta"))
        val getMonth = GetMonthMovementsUseCase(movements, com.syncro.testutil.FakeSettingsRepository())

        assertEquals(listOf("conjunto"), getMonth(DAY, "conjunta").first().occurrences.map { it.movement.id })
        assertEquals(setOf("mio", "conjunto"), getMonth(DAY).first().occurrences.map { it.movement.id }.toSet())
    }
}
