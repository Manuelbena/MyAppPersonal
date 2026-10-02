package com.syncro.domain.model

import com.syncro.testutil.aMovement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Plan: las reglas del dinero. Responsabilidades: leer lo que se teclea como céntimos exactos,
 * decidir en qué día cae cada movimiento en un mes (los mensuales, todos los meses) y sumar los
 * totales. Riesgos: redondeos (por eso céntimos enteros), un recibo del 31 que desaparece en los
 * meses cortos, y totales que mezclan meses.
 */
class MovementTest {

    // region Importe tecleado

    @Test
    fun `el importe admite coma o punto y hasta dos decimales`() {
        assertEquals(1_200L, parseEuroCents("12"))
        assertEquals(1_250L, parseEuroCents("12,5"))
        assertEquals(1_250L, parseEuroCents("12.50"))
        assertEquals(5L, parseEuroCents("0,05"))
        assertEquals(1_200L, parseEuroCents(" 12 € "))
    }

    @Test
    fun `un importe no valido o cero no se acepta`() {
        listOf("", "0", "0,00", "abc", "12,345", "1.234,56", "-5", "12,", ",5").forEach {
            assertNull("\"$it\" no debería aceptarse", parseEuroCents(it))
        }
    }

    @Test
    fun `no hay redondeos al sumar centimos`() {
        // Con Double 0,10 + 0,20 = 0,30000000000000004: en céntimos es exacto
        assertEquals(30L, parseEuroCents("0,10")!! + parseEuroCents("0,20")!!)
    }

    // endregion

    // region Día en cada mes

    @Test
    fun `un movimiento puntual solo cuenta en su mes`() {
        val movement = aMovement(date = LocalDate.of(2026, 10, 5))

        assertEquals(LocalDate.of(2026, 10, 5), movement.dateIn(YearMonth.of(2026, 10)))
        assertNull(movement.dateIn(YearMonth.of(2026, 11)))
        assertNull(movement.dateIn(YearMonth.of(2026, 9)))
    }

    @Test
    fun `uno mensual cuenta desde su mes en adelante, el mismo dia`() {
        val rent = aMovement(date = LocalDate.of(2026, 9, 1), repeatsMonthly = true)

        assertNull(rent.dateIn(YearMonth.of(2026, 8)))
        assertEquals(LocalDate.of(2026, 9, 1), rent.dateIn(YearMonth.of(2026, 9)))
        assertEquals(LocalDate.of(2027, 3, 1), rent.dateIn(YearMonth.of(2027, 3)))
    }

    @Test
    fun `un recibo del 31 cae el ultimo dia de los meses cortos`() {
        val bill = aMovement(date = LocalDate.of(2026, 1, 31), repeatsMonthly = true)

        assertEquals(LocalDate.of(2026, 2, 28), bill.dateIn(YearMonth.of(2026, 2)))
        assertEquals(LocalDate.of(2028, 2, 29), bill.dateIn(YearMonth.of(2028, 2)))
        assertEquals(LocalDate.of(2026, 4, 30), bill.dateIn(YearMonth.of(2026, 4)))
        assertEquals(LocalDate.of(2026, 5, 31), bill.dateIn(YearMonth.of(2026, 5)))
    }

    // endregion

    // region Totales del mes

    @Test
    fun `los totales del mes suman ingresos y gastos por separado`() {
        val october = YearMonth.of(2026, 10)
        val month = monthMovements(
            october,
            listOf(
                aMovement(id = "nomina", type = MovementType.INCOME, amountCents = 200_000, category = MovementCategory.SALARY, date = LocalDate.of(2026, 9, 28), repeatsMonthly = true),
                aMovement(id = "super", amountCents = 8_550, date = LocalDate.of(2026, 10, 3)),
                aMovement(id = "alquiler", amountCents = 70_000, category = MovementCategory.HOUSING, date = LocalDate.of(2026, 8, 1), repeatsMonthly = true),
                // De otro mes: no cuenta
                aMovement(id = "septiembre", amountCents = 99_999, date = LocalDate.of(2026, 9, 15))
            )
        )

        assertEquals(200_000L, month.incomeCents)
        assertEquals(78_550L, month.expenseCents)
        assertEquals(121_450L, month.balanceCents)
        assertEquals(60, month.savingsRatePercent)
        // Más reciente primero
        assertEquals(listOf("nomina", "super", "alquiler"), month.occurrences.map { it.movement.id })
    }

    @Test
    fun `sin ingresos no hay tasa de ahorro, y gastar de mas la hace negativa`() {
        val october = YearMonth.of(2026, 10)
        assertNull(monthMovements(october, listOf(aMovement(date = LocalDate.of(2026, 10, 1)))).savingsRatePercent)

        val overspent = monthMovements(
            october,
            listOf(
                aMovement(id = "i", type = MovementType.INCOME, amountCents = 100_000, category = MovementCategory.SALARY, date = LocalDate.of(2026, 10, 1)),
                aMovement(id = "g", amountCents = 125_000, date = LocalDate.of(2026, 10, 2))
            )
        )
        assertEquals(-25, overspent.savingsRatePercent)
    }

    // endregion
}
