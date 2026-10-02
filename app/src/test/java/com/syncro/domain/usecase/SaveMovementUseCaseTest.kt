package com.syncro.domain.usecase

import com.syncro.domain.model.InvalidAmountException
import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.MovementType
import com.syncro.testutil.DAY
import com.syncro.testutil.FakeMovementRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan: guardar un ingreso o gasto. Riesgos: guardar importes no válidos, que un gasto acabe en
 * una categoría de ingresos (descuadra los totales) y conceptos vacíos que se ven como "".
 */
class SaveMovementUseCaseTest {

    private val repository = FakeMovementRepository()
    private val save = SaveMovementUseCase(repository)

    @Test
    fun `guarda el movimiento con un id nuevo y el concepto en mayuscula`() = runTest {
        val result = save(MovementType.EXPENSE, 4_590, MovementCategory.GROCERIES, DAY, "  mercadona ", repeatsMonthly = false)

        assertTrue(result.isSuccess)
        val saved = repository.movements.value.values.single()
        assertTrue(saved.id.isNotBlank())
        assertEquals(4_590L, saved.amountCents)
        assertEquals("Mercadona", saved.note)
        assertEquals(DAY, saved.date)
    }

    @Test
    fun `un importe de cero o negativo falla sin guardar nada`() = runTest {
        listOf(0L, -100L).forEach { cents ->
            val result = save(MovementType.EXPENSE, cents, MovementCategory.GROCERIES, DAY, "", false)
            assertTrue(result.exceptionOrNull() is InvalidAmountException)
        }

        assertTrue(repository.movements.value.isEmpty())
    }

    @Test
    fun `un concepto vacio se guarda como sin concepto`() = runTest {
        save(MovementType.INCOME, 200_000, MovementCategory.SALARY, DAY, "   ", repeatsMonthly = true)

        val saved = repository.movements.value.values.single()
        assertNull(saved.note)
        assertTrue(saved.repeatsMonthly)
    }

    @Test
    fun `una categoria del otro tipo se guarda en Otros del tipo correcto`() = runTest {
        save(MovementType.EXPENSE, 1_000, MovementCategory.SALARY, DAY, "", false)

        assertEquals(MovementCategory.OTHER_EXPENSE, repository.movements.value.values.single().category)
    }
}
