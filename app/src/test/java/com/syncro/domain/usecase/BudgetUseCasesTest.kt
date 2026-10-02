package com.syncro.domain.usecase

import com.syncro.domain.model.Budget
import com.syncro.domain.model.InvalidBudgetException
import com.syncro.domain.model.MovementCategory
import com.syncro.testutil.FakeBudgetRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plan: crear, cambiar y quitar presupuestos. Riesgos: límites de ingresos o de 0 €, y duplicados. */
class BudgetUseCasesTest {

    private val repository = FakeBudgetRepository()
    private val save = SaveBudgetUseCase(repository)
    private val get = GetBudgetsUseCase(repository)
    private val delete = DeleteBudgetUseCase(repository)

    @Test
    fun `guardar otra vez la misma categoria cambia su limite`() = runTest {
        save(MovementCategory.GROCERIES, 30_000)
        save(MovementCategory.GROCERIES, 35_000)

        assertEquals(listOf(Budget(MovementCategory.GROCERIES, 35_000)), get().first())
    }

    @Test
    fun `no se puede poner presupuesto a un ingreso ni de 0 euros`() = runTest {
        assertTrue(save(MovementCategory.SALARY, 30_000).exceptionOrNull() is InvalidBudgetException)
        assertTrue(save(MovementCategory.GROCERIES, 0).exceptionOrNull() is InvalidBudgetException)

        assertTrue(get().first().isEmpty())
    }

    @Test
    fun `quitar un presupuesto lo borra`() = runTest {
        save(MovementCategory.LEISURE, 10_000)

        delete(MovementCategory.LEISURE)

        assertTrue(get().first().isEmpty())
    }
}
