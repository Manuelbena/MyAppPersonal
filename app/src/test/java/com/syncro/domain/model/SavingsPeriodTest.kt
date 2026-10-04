package com.syncro.domain.model

import com.syncro.testutil.aMovement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Plan: el "mes" de Ahorros de nómina a nómina. Responsabilidades: en qué periodo cae cada día,
 * qué movimientos cuentan en él (los mensuales una vez aunque abarque dos meses) y el mes natural
 * sin nómina. Riesgos: un día que cae en dos periodos o en ninguno, una nómina del 31 en meses
 * cortos, un recibo mensual contado dos veces o ninguna, y el cambio de año.
 */
class SavingsPeriodTest {

    private fun date(month: Int, day: Int, year: Int = 2026) = LocalDate.of(year, month, day)

    @Test
    fun `sin nomina el periodo es el mes natural`() {
        val period = savingsPeriodOf(date(10, 15), paydayDay = null)

        assertEquals(SavingsPeriod.of(YearMonth.of(2026, 10)), period)
        assertTrue(period.isCalendarMonth)
        // Cobrar el día 1 es lo mismo que el mes natural
        assertEquals(period, savingsPeriodOf(date(10, 15), paydayDay = 1))
    }

    @Test
    fun `cobrando el 27 va del 27 al 26 del mes siguiente`() {
        val period = SavingsPeriod(date(9, 27), date(10, 26))

        assertEquals(period, savingsPeriodOf(date(9, 27), 27))
        assertEquals(period, savingsPeriodOf(date(10, 2), 27))
        assertEquals(period, savingsPeriodOf(date(10, 26), 27))
        assertEquals(SavingsPeriod(date(10, 27), date(11, 26)), savingsPeriodOf(date(10, 27), 27))
        assertFalse(period.isCalendarMonth)
        assertEquals(YearMonth.of(2026, 9), period.month)
    }

    @Test
    fun `cobrando el 31 la nomina cae el ultimo dia de los meses cortos`() {
        // En febrero se cobra el 28; el siguiente periodo empieza el 31 de marzo
        assertEquals(SavingsPeriod(date(1, 31), date(2, 27)), savingsPeriodOf(date(2, 10), 31))
        assertEquals(SavingsPeriod(date(2, 28), date(3, 30)), savingsPeriodOf(date(3, 1), 31))
        assertEquals(SavingsPeriod(date(3, 31), date(4, 29)), savingsPeriodOf(date(4, 29), 31))
    }

    @Test
    fun `cada dia del ano cae en un solo periodo y los periodos van seguidos`() {
        for (payday in listOf(1, 15, 27, 29, 30, 31)) {
            var day = date(1, 1)
            var previous = savingsPeriodOf(day, payday)
            while (day.year == 2026) {
                val period = savingsPeriodOf(day, payday)
                assertTrue("$day con nómina el $payday", day in period)
                if (period != previous) assertEquals(previous.end.plusDays(1), period.start)
                previous = period
                day = day.plusDays(1)
            }
        }
    }

    @Test
    fun `el periodo cruza el cambio de ano`() {
        assertEquals(SavingsPeriod(date(12, 27), date(1, 26, 2027)), savingsPeriodOf(date(1, 5, 2027), 27))
    }

    @Test
    fun `un movimiento mensual cuenta una vez en un periodo que abarca dos meses`() {
        val period = SavingsPeriod(date(9, 27), date(10, 26))
        val rent = aMovement(id = "alquiler", date = date(1, 1), repeatsMonthly = true)
        val netflix = aMovement(id = "netflix", date = date(1, 28), repeatsMonthly = true)
        val once = aMovement(id = "cena", date = date(9, 30))
        val before = aMovement(id = "antes", date = date(9, 26))

        val month = monthMovements(period, listOf(rent, netflix, once, before))

        assertEquals(
            listOf("alquiler" to date(10, 1), "cena" to date(9, 30), "netflix" to date(9, 28)),
            month.occurrences.map { it.movement.id to it.date }
        )
    }

    @Test
    fun `un mensual que empieza a mitad de periodo no cuenta antes de su fecha`() {
        val period = SavingsPeriod(date(9, 27), date(10, 26))
        val gym = aMovement(id = "gimnasio", date = date(10, 5), repeatsMonthly = true)

        assertEquals(listOf(date(10, 5)), gym.datesIn(period))
        assertEquals(emptyList<LocalDate>(), gym.datesIn(SavingsPeriod(date(8, 27), date(9, 26))))
    }

    @Test
    fun `los avisos de presupuesto llevan el mes en que empieza el periodo`() {
        val groceries = Budget(MovementCategory.GROCERIES, 10_000)
        val period = SavingsPeriod(date(9, 27), date(10, 26))
        val month = monthMovements(period, listOf(aMovement(id = "m", amountCents = 9_000, category = MovementCategory.GROCERIES, date = date(10, 3))))

        val alert = month.budgetAlerts(listOf(groceries), today = date(10, 4)).single()

        // Si llevara octubre, chocaría con el aviso del periodo siguiente (27 oct – 26 nov)
        assertEquals(YearMonth.of(2026, 9), alert.month)
    }
}
