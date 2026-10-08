package com.syncro.domain.model

import com.syncro.testutil.aMovement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Plan: presupuestos agrupados con el 50/30/20. Responsabilidades: a qué grupo va cada categoría,
 * cuánto le toca a cada grupo (de la nómina o, sin ella, de lo que ha entrado), cuánto se ha
 * gastado en él y cuánto se ha ahorrado. Riesgos: una categoría de gasto sin grupo (su gasto no
 * contaría), objetivos que no suman lo que entra, y contar solo lo que tiene presupuesto.
 */
class BudgetGroupsTest {

    private val october = YearMonth.of(2026, 10)
    private fun day(d: Int) = LocalDate.of(2026, 10, d)

    private fun salary(cents: Long) =
        aMovement(id = "nomina", type = MovementType.INCOME, amountCents = cents, category = MovementCategory.SALARY, date = day(1))

    private fun spend(id: String, cents: Long, category: MovementCategory) =
        aMovement(id = id, amountCents = cents, category = category, date = day(2))

    @Test
    fun `cada categoria de gasto tiene grupo y las de ingresos no`() {
        MovementCategory.of(MovementType.EXPENSE).forEach { assertTrue("$it sin grupo", it.budgetGroup != null) }
        MovementCategory.of(MovementType.INCOME).forEach { assertNull(it.budgetGroup) }
        assertEquals(BudgetGroup.NEEDS, MovementCategory.HOUSING.budgetGroup)
        assertEquals(BudgetGroup.WANTS, MovementCategory.RESTAURANTS.budgetGroup)
    }

    @Test
    fun `con nomina cada grupo tiene su parte y todo suma la nomina`() {
        val plan = monthMovements(october, listOf(salary(180_000))).budgetPlan(emptyList())

        assertEquals(BudgetBase(180_000, isSalary = true), plan.base)
        assertEquals(listOf(90_000L, 54_000L), plan.groups.map { it.targetCents })
        assertEquals(36_000L, plan.savings?.targetCents)
    }

    @Test
    fun `lo gastado en un grupo cuenta todas sus categorias, tengan presupuesto o no`() {
        val month = monthMovements(
            october,
            listOf(
                salary(180_000),
                spend("alquiler", 70_000, MovementCategory.HOUSING),
                spend("super", 25_000, MovementCategory.GROCERIES),
                spend("cine", 3_000, MovementCategory.LEISURE)
            )
        )

        val plan = month.budgetPlan(listOf(Budget(MovementCategory.GROCERIES, 30_000)))

        val needs = plan.groups.first { it.group == BudgetGroup.NEEDS }
        assertEquals(95_000L, needs.spentCents)
        assertEquals(105, needs.percent)
        assertEquals(BudgetLevel.EXCEEDED, needs.level)
        assertEquals(listOf(MovementCategory.GROCERIES), needs.statuses.map { it.budget.category })
        assertEquals(3_000L, plan.groups.first { it.group == BudgetGroup.WANTS }.spentCents)
        // Ahorro: 180.000 − 98.000
        assertEquals(82_000L, plan.savings?.savedCents)
        assertTrue(plan.savings!!.reached)
    }

    @Test
    fun `sin nomina el reparto sale de lo que ha entrado`() {
        val freelance = aMovement(id = "factura", type = MovementType.INCOME, amountCents = 100_000, category = MovementCategory.EXTRA_WORK, date = day(1))

        val plan = monthMovements(october, listOf(freelance)).budgetPlan(emptyList())

        assertEquals(BudgetBase(100_000, isSalary = false), plan.base)
        assertEquals(50_000L, plan.groups.first().targetCents)
    }

    @Test
    fun `sin ingresos no hay objetivos y el grupo va como su peor presupuesto`() {
        val month = monthMovements(october, listOf(spend("super", 28_000, MovementCategory.GROCERIES)))

        val plan = month.budgetPlan(listOf(Budget(MovementCategory.GROCERIES, 30_000)))

        assertNull(plan.base)
        assertNull(plan.savings)
        val needs = plan.groups.first { it.group == BudgetGroup.NEEDS }
        assertNull(needs.targetCents)
        assertEquals(BudgetLevel.WARNING, needs.level)
        assertTrue(needs.needsAttention)
        assertFalse(plan.groups.first { it.group == BudgetGroup.WANTS }.needsAttention)
    }
}
