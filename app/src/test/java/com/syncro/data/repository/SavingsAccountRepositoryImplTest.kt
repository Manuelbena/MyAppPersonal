package com.syncro.data.repository

import com.syncro.data.local.SyncroDatabase
import com.syncro.domain.model.ArgbColor
import com.syncro.domain.model.Budget
import com.syncro.domain.model.MAIN_ACCOUNT_ID
import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.SavingsAccount
import com.syncro.domain.model.SavingsPeriod
import com.syncro.testutil.DAY
import com.syncro.testutil.aMovement
import com.syncro.testutil.createInMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.YearMonth

/**
 * Plan de pruebas de [SavingsAccountRepositoryImpl] con Room de verdad.
 *
 * Responsabilidades: que siempre haya una cuenta (también tras vaciar la base de datos al cerrar
 * sesión), que solo una sea la que se ve, y borrar una cuenta con lo suyo y nada más.
 *
 * Riesgos: quedarse sin cuentas (la pantalla de Ahorros no tendría nada que enseñar), dos cuentas
 * marcadas a la vez, y borrar movimientos o presupuestos de otra cuenta.
 */
@RunWith(RobolectricTestRunner::class)
class SavingsAccountRepositoryImplTest {

    private lateinit var db: SyncroDatabase
    private lateinit var repository: SavingsAccountRepositoryImpl

    @Before
    fun setUp() {
        db = createInMemoryDatabase()
        repository = SavingsAccountRepositoryImpl(db.savingsAccountDao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val conjunta = SavingsAccount("conjunta", "Conjunta", ArgbColor(0xFF0EA5E9))

    @Test
    fun `sin cuentas se crea la principal y es la que se ve`() = runTest {
        val accounts = repository.observeAccounts().first()

        assertEquals(listOf(MAIN_ACCOUNT_ID), accounts.all.map { it.id })
        assertEquals(MAIN_ACCOUNT_ID, accounts.active.id)
    }

    @Test
    fun `una cuenta nueva va al final y solo se ve la elegida`() = runTest {
        repository.observeAccounts().first()
        repository.saveAccount(conjunta)
        repository.selectAccount("conjunta")

        val accounts = repository.observeAccounts().first()

        assertEquals(listOf(MAIN_ACCOUNT_ID, "conjunta"), accounts.all.map { it.id })
        assertEquals("conjunta", accounts.active.id)
        assertEquals(1, db.savingsAccountDao.getAll().count { it.isActive })
    }

    @Test
    fun `renombrar conserva su sitio y si es la que se ve`() = runTest {
        repository.observeAccounts().first()
        repository.saveAccount(conjunta)
        repository.selectAccount("conjunta")

        repository.saveAccount(conjunta.copy(name = "Casa"))

        val accounts = repository.observeAccounts().first()
        assertEquals(listOf("Principal", "Casa"), accounts.all.map { it.name })
        assertEquals("conjunta", accounts.active.id)
    }

    @Test
    fun `borrar una cuenta se lleva sus movimientos y presupuestos y nada de las demas`() = runTest {
        repository.observeAccounts().first()
        repository.saveAccount(conjunta)
        val movements = MovementRepositoryImpl(db.movementDao)
        val budgets = BudgetRepositoryImpl(db.budgetDao)
        movements.insertMovement(aMovement(id = "suyo", accountId = "conjunta"))
        movements.insertMovement(aMovement(id = "principal"))
        budgets.saveBudget(Budget(MovementCategory.GROCERIES, 30_000, "conjunta"))
        budgets.saveBudget(Budget(MovementCategory.GROCERIES, 20_000))

        repository.deleteAccount("conjunta")

        assertEquals(listOf("principal"), movements.getAllMovements().map { it.id })
        assertEquals(listOf(Budget(MovementCategory.GROCERIES, 20_000)), budgets.observeBudgets().first())
        assertEquals(listOf(MAIN_ACCOUNT_ID), repository.observeAccounts().first().all.map { it.id })
    }

    @Test
    fun `tras vaciar la base de datos (cerrar sesion) vuelve a haber una principal`() = runTest {
        repository.saveAccount(conjunta)
        db.clearAllTables()

        assertEquals(listOf(MAIN_ACCOUNT_ID), repository.observeAccounts().first().all.map { it.id })
    }

    @Test
    fun `una misma categoria puede tener presupuesto en cada cuenta`() = runTest {
        val budgets = BudgetRepositoryImpl(db.budgetDao)
        budgets.saveBudget(Budget(MovementCategory.GROCERIES, 30_000, "conjunta"))
        budgets.saveBudget(Budget(MovementCategory.GROCERIES, 20_000))

        assertEquals(listOf(Budget(MovementCategory.GROCERIES, 30_000, "conjunta")), budgets.observeBudgets("conjunta").first())
        assertEquals(2, budgets.observeBudgets().first().size)
    }

    @Test
    fun `los movimientos de una cuenta no se mezclan con los de otra`() = runTest {
        val movements = MovementRepositoryImpl(db.movementDao)
        movements.insertMovement(aMovement(id = "suyo", accountId = "conjunta"))
        movements.insertMovement(aMovement(id = "principal"))
        val october = SavingsPeriod.of(YearMonth.from(DAY))

        assertEquals(listOf("suyo"), movements.observeForPeriod(october, "conjunta").first().map { it.id })
        assertEquals(2, movements.observeForPeriod(october).first().size)
    }
}
