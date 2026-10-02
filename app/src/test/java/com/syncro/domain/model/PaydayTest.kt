package com.syncro.domain.model

import com.syncro.testutil.aMovement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Plan: el día de nómina. Responsabilidades: en qué día cae cada mes, cuál fue el último, cuánto
 * se ha apuntado de nómina y el reparto 50/30/20. Riesgos: una nómina del 31 que desaparece en los
 * meses cortos, el día de nómina de un mes que aún no ha llegado, y un reparto que no suma la nómina.
 */
class PaydayTest {

    @Test
    fun `la nomina cae su dia o el ultimo del mes si es mas corto`() {
        assertEquals(LocalDate.of(2026, 10, 28), paydayIn(YearMonth.of(2026, 10), 28))
        assertEquals(LocalDate.of(2026, 2, 28), paydayIn(YearMonth.of(2026, 2), 31))
        assertEquals(LocalDate.of(2026, 4, 30), paydayIn(YearMonth.of(2026, 4), 31))
    }

    @Test
    fun `la ultima nomina es la de este mes si ya llego, si no la del anterior`() {
        assertEquals(LocalDate.of(2026, 10, 28), lastPayday(28, LocalDate.of(2026, 10, 28)))
        assertEquals(LocalDate.of(2026, 9, 28), lastPayday(28, LocalDate.of(2026, 10, 27)))
        // Del 31: en septiembre (30 días) fue el 30
        assertEquals(LocalDate.of(2026, 9, 30), lastPayday(31, LocalDate.of(2026, 10, 5)))
    }

    @Test
    fun `el reparto 50-30-20 suma exactamente la nomina`() {
        val plan = salaryPlan(185_001)

        assertEquals(37_000L, plan.savingsCents)
        assertEquals(55_500L, plan.wantsCents)
        // El céntimo del redondeo va a lo necesario
        assertEquals(92_501L, plan.needsCents)
        assertEquals(185_001L, plan.needsCents + plan.wantsCents + plan.savingsCents)
    }

    @Test
    fun `la nomina apuntada son los ingresos de la categoria Nomina del mes`() {
        val october = YearMonth.of(2026, 10)
        val month = monthMovements(
            october,
            listOf(
                aMovement(id = "n", type = MovementType.INCOME, amountCents = 185_000, category = MovementCategory.SALARY, date = LocalDate.of(2026, 10, 28)),
                aMovement(id = "v", type = MovementType.INCOME, amountCents = 4_500, category = MovementCategory.SALES, date = LocalDate.of(2026, 10, 5)),
                aMovement(id = "g", amountCents = 5_000, date = LocalDate.of(2026, 10, 2))
            )
        )

        assertEquals(185_000L, month.salaryCents())
        assertNull(monthMovements(october, emptyList()).salaryCents())
    }
}
