package com.syncro.data.repository

import com.syncro.data.local.SyncroDatabase
import com.syncro.data.local.entity.BudgetEntity
import com.syncro.domain.model.Budget
import com.syncro.domain.model.MovementCategory
import com.syncro.testutil.createInMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Plan de pruebas de [BudgetRepositoryImpl]
 *
 * Responsabilidades: un presupuesto por categoría (guardar de nuevo lo cambia), borrar, y leer
 * sin romperse con filas raras. Riesgos: el REPLACE de la clave (la categoría) duplicando filas y
 * categorías renombradas o de ingresos en la tabla.
 */
@RunWith(RobolectricTestRunner::class)
class BudgetRepositoryImplTest {

    private lateinit var db: SyncroDatabase
    private lateinit var repository: BudgetRepositoryImpl

    @Before
    fun setUp() {
        db = createInMemoryDatabase()
        repository = BudgetRepositoryImpl(db.budgetDao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `guardar la misma categoria sustituye el limite`() = runTest {
        repository.saveBudget(Budget(MovementCategory.GROCERIES, 30_000))
        repository.saveBudget(Budget(MovementCategory.GROCERIES, 40_000))
        repository.saveBudget(Budget(MovementCategory.LEISURE, 10_000))

        assertEquals(
            setOf(Budget(MovementCategory.GROCERIES, 40_000), Budget(MovementCategory.LEISURE, 10_000)),
            repository.observeBudgets().first().toSet()
        )
    }

    @Test
    fun `borrar quita solo esa categoria`() = runTest {
        repository.saveBudget(Budget(MovementCategory.GROCERIES, 30_000))
        repository.saveBudget(Budget(MovementCategory.LEISURE, 10_000))

        repository.deleteBudget(MovementCategory.GROCERIES)

        assertEquals(listOf(Budget(MovementCategory.LEISURE, 10_000)), repository.observeBudgets().first())
    }

    @Test
    fun `una categoria desconocida o de ingresos se ignora`() = runTest {
        db.budgetDao.saveBudget(BudgetEntity("CATEGORIA_RENOMBRADA", 1_000))
        db.budgetDao.saveBudget(BudgetEntity("SALARY", 1_000))
        repository.saveBudget(Budget(MovementCategory.HOUSING, 70_000))

        assertEquals(listOf(Budget(MovementCategory.HOUSING, 70_000)), repository.observeBudgets().first())
    }
}
