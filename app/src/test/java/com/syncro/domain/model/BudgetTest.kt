package com.syncro.domain.model

import com.syncro.testutil.aMovement
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Plan: los presupuestos. Responsabilidades: cuánto se lleva gastado frente al límite, el estado
 * (bien, ojo al 80 %, pasado) y los avisos con el día exacto en que se cruzó cada umbral.
 * Riesgos: contar ingresos u otras categorías, el límite justo (100 % aún no es pasarse), dos
 * avisos el mismo día si un gasto lo pasa de golpe y avisar de gastos que aún no han llegado.
 */
class BudgetTest {

    private val october = YearMonth.of(2026, 10)
    private val groceries = Budget(MovementCategory.GROCERIES, 30_000)

    private fun spend(id: String, cents: Long, day: Int, category: MovementCategory = MovementCategory.GROCERIES) =
        aMovement(id = id, amountCents = cents, category = category, date = LocalDate.of(2026, 10, day))

    @Test
    fun `cuenta solo los gastos de su categoria`() {
        val month = monthMovements(
            october,
            listOf(
                spend("a", 10_000, 1),
                spend("b", 5_000, 3),
                spend("ocio", 99_000, 2, MovementCategory.LEISURE),
                aMovement(id = "dev", type = MovementType.INCOME, amountCents = 50_000, category = MovementCategory.REFUNDS, date = LocalDate.of(2026, 10, 4))
            )
        )

        val status = month.budgetStatuses(listOf(groceries)).single()
        assertEquals(15_000L, status.spentCents)
        assertEquals(50, status.percent)
        assertEquals(15_000L, status.remainingCents)
        assertEquals(BudgetLevel.OK, status.level)
    }

    @Test
    fun `desde el 80 por ciento es ojo, justo el limite aun no es pasarse y mas si`() {
        assertEquals(BudgetLevel.OK, BudgetStatus(groceries, 23_999).level)
        assertEquals(BudgetLevel.WARNING, BudgetStatus(groceries, 24_000).level)
        assertEquals(BudgetLevel.WARNING, BudgetStatus(groceries, 30_000).level)
        assertEquals(BudgetLevel.EXCEEDED, BudgetStatus(groceries, 30_001).level)
        assertEquals(-1L, BudgetStatus(groceries, 30_001).remainingCents)
    }

    @Test
    fun `los mas apurados primero`() {
        val leisure = Budget(MovementCategory.LEISURE, 10_000)
        val month = monthMovements(october, listOf(spend("a", 6_000, 1), spend("o", 9_000, 1, MovementCategory.LEISURE)))

        assertEquals(listOf(MovementCategory.LEISURE, MovementCategory.GROCERIES), month.budgetStatuses(listOf(groceries, leisure)).map { it.budget.category })
    }

    @Test
    fun `avisa el dia que llega al 80 y el dia que se pasa`() {
        val month = monthMovements(october, listOf(spend("a", 20_000, 2), spend("b", 5_000, 9), spend("c", 6_000, 15)))

        assertEquals(
            listOf(
                BudgetAlert(groceries, BudgetLevel.WARNING, LocalDate.of(2026, 10, 9), 25_000),
                BudgetAlert(groceries, BudgetLevel.EXCEEDED, LocalDate.of(2026, 10, 15), 31_000)
            ),
            month.budgetAlerts(listOf(groceries), today = LocalDate.of(2026, 10, 20))
        )
    }

    @Test
    fun `un gasto que lo pasa de golpe da un solo aviso`() {
        val month = monthMovements(october, listOf(spend("a", 35_000, 4)))

        assertEquals(
            listOf(BudgetAlert(groceries, BudgetLevel.EXCEEDED, LocalDate.of(2026, 10, 4), 35_000)),
            month.budgetAlerts(listOf(groceries), today = LocalDate.of(2026, 10, 20))
        )
    }

    @Test
    fun `los gastos que aun no han llegado no avisan`() {
        // El 28 cae un recibo que lo pasaría, pero hoy es día 10
        val month = monthMovements(october, listOf(spend("a", 20_000, 2), spend("recibo", 20_000, 28)))

        assertEquals(emptyList<BudgetAlert>(), month.budgetAlerts(listOf(groceries), today = LocalDate.of(2026, 10, 10)))
        // En el estado sí cuenta: ese dinero ya está comprometido
        assertEquals(BudgetLevel.EXCEEDED, month.budgetStatuses(listOf(groceries)).single().level)
    }
}
