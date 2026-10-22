package com.syncro.domain.model

import com.syncro.testutil.aMovement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Datos de las gráficas del desglose: el gasto acumulado por día (continuo, con lo previsto y sin
 * ingresos) y "¿En qué se va?" (las 5 categorías mayores y el resto en "Otros").
 */
class SpendingChartsTest {

    private val october = YearMonth.of(2026, 10)
    private fun day(d: Int) = LocalDate.of(2026, 10, d)

    @Test
    fun `el acumulado tiene un punto por dia y solo suma gastos`() {
        val month = monthMovements(
            october,
            listOf(
                aMovement(id = "a", amountCents = 1_000, date = day(2)),
                aMovement(id = "b", amountCents = 500, date = day(2)),
                aMovement(id = "nomina", type = MovementType.INCOME, category = MovementCategory.SALARY, amountCents = 200_000, date = day(3)),
                aMovement(id = "c", amountCents = 2_000, date = day(10))
            )
        )

        val points = month.cumulativeSpending()

        assertEquals(31, points.size)
        assertEquals(0L, points[0].cents)
        assertEquals(1_500L, points[1].cents)
        assertEquals(1_500L, points[8].cents)
        assertEquals(3_500L, points.last().cents)
    }

    @Test
    fun `un gasto mensual cuenta en su dia de este mes`() {
        val rent = aMovement(id = "alquiler", amountCents = 70_000, category = MovementCategory.HOUSING, date = LocalDate.of(2026, 1, 5), repeatsMonthly = true)

        val points = monthMovements(october, listOf(rent)).cumulativeSpending()

        assertEquals(0L, points[3].cents)
        assertEquals(70_000L, points[4].cents)
    }

    @Test
    fun `se ven las 5 mayores y el resto junto en Otros, con Otros gastos siempre ahi`() {
        val categories = listOf(
            MovementCategory.HOUSING to 50_000L, MovementCategory.GROCERIES to 30_000L, MovementCategory.TRANSPORT to 10_000L,
            MovementCategory.LEISURE to 8_000L, MovementCategory.RESTAURANTS to 6_000L, MovementCategory.HEALTH to 4_000L,
            MovementCategory.OTHER_EXPENSE to 20_000L
        )
        val month = monthMovements(october, categories.mapIndexed { i, (category, cents) -> aMovement(id = "m$i", category = category, amountCents = cents, date = day(1)) })

        val shares = month.expenseShares()

        assertEquals(
            listOf(MovementCategory.HOUSING, MovementCategory.GROCERIES, MovementCategory.TRANSPORT, MovementCategory.LEISURE, MovementCategory.RESTAURANTS, null),
            shares.map { it.category }
        )
        // Otros = Salud + Otros gastos
        assertEquals(24_000L, shares.last().cents)
        assertEquals(39, shares.first().percent)
    }

    @Test
    fun `sin gastos no hay barras`() {
        assertEquals(emptyList<CategoryShare>(), monthMovements(october, emptyList()).expenseShares())
    }

    @Test
    fun `lo que se puede gastar es el 80 por ciento de la base, y sin base no hay`() {
        val salary = aMovement(id = "nomina", type = MovementType.INCOME, category = MovementCategory.SALARY, amountCents = 200_000, date = day(1))

        assertEquals(160_000L, monthMovements(october, listOf(salary)).budgetPlan(emptyList()).spendingLimitCents)
        assertNull(monthMovements(october, emptyList()).budgetPlan(emptyList()).spendingLimitCents)
    }
}
