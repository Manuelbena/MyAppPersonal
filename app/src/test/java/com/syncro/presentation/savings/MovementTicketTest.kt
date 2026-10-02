package com.syncro.presentation.savings

import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.MovementOccurrence
import com.syncro.domain.model.MovementType
import com.syncro.domain.model.monthMovements
import com.syncro.presentation.savings.TicketBlock.Heading
import com.syncro.presentation.savings.TicketBlock.Row
import com.syncro.presentation.savings.TicketBlock.Total
import com.syncro.testutil.aMovement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * Plan: lo que dicen los tickets que se comparten (el de un movimiento y el resumen del mes) y sus
 * pies de foto. Riesgos: que falte un dato, que la fecha de un mensual sea la de origen en vez de
 * la del mes, totales o % del desglose mal sumados, y que parezca un documento oficial.
 */
class MovementTicketTest {

    private val issuedAt = LocalDateTime.of(2026, 10, 2, 18, 5)
    private val nbsp = " "

    private fun occurrence(note: String?, repeats: Boolean = false, type: MovementType = MovementType.EXPENSE) =
        MovementOccurrence(
            aMovement(
                id = "3f2a9c1b-0000-4000-8000-000000000000",
                type = type,
                amountCents = 4_590,
                category = if (type == MovementType.EXPENSE) MovementCategory.GROCERIES else MovementCategory.SALARY,
                date = LocalDate.of(2026, 9, 1),
                note = note,
                repeatsMonthly = repeats
            ),
            date = LocalDate.of(2026, 10, 1)
        )

    private fun TicketContent.rows() = blocks.filterIsInstance<Row>().map { it.label to it.value }

    // region Ticket de un movimiento

    @Test
    fun `el ticket lleva fecha del mes, tipo, categoria, concepto, frecuencia y total`() {
        val ticket = movementTicket(occurrence("Mercadona", repeats = true), issuedAt)

        assertEquals("Justificante de gasto", ticket.subtitle)
        assertEquals(
            listOf(
                // La fecha es la del mes que se mira (los mensuales caen cada mes)
                "Fecha" to "01/10/2026",
                "Tipo" to "Gasto",
                "Categoría" to "Supermercado",
                "Concepto" to "Mercadona",
                "Frecuencia" to "Mensual"
            ),
            ticket.rows()
        )
        assertEquals(Total("TOTAL", "−45,90$nbsp€", MovementType.EXPENSE), ticket.blocks.filterIsInstance<Total>().single())
    }

    @Test
    fun `sin concepto ni repeticion no salen esas filas`() {
        assertEquals(listOf("Fecha", "Tipo", "Categoría"), movementTicket(occurrence(null), issuedAt).rows().map { it.first })
    }

    @Test
    fun `el pie lleva referencia, fecha de emision y deja claro que no es una factura`() {
        val footer = movementTicket(occurrence(null), issuedAt).footer

        assertEquals("Ref. 3F2A9C1B", footer[0])
        assertEquals("Emitido el 02/10/2026 18:05", footer[1])
        assertTrue(footer[2].contains("No es una factura ni un justificante bancario"))
    }

    @Test
    fun `el pie de foto resume el movimiento en pocas lineas`() {
        assertEquals("Gasto · Supermercado\n−45,90$nbsp€ · 01/10/2026\nMercadona", movementShareText(occurrence("Mercadona")))
        assertEquals("Ingreso · Nómina\n+45,90$nbsp€ · 01/10/2026", movementShareText(occurrence(null, type = MovementType.INCOME)))
    }

    // endregion

    // region Resumen del mes

    private val october = monthMovements(
        YearMonth.of(2026, 10),
        listOf(
            aMovement(id = "nomina", type = MovementType.INCOME, amountCents = 200_000, category = MovementCategory.SALARY, date = LocalDate.of(2026, 9, 1), note = "Nómina", repeatsMonthly = true),
            aMovement(id = "alquiler", amountCents = 75_000, category = MovementCategory.HOUSING, date = LocalDate.of(2026, 10, 1), note = "Alquiler"),
            aMovement(id = "super", amountCents = 15_000, category = MovementCategory.GROCERIES, date = LocalDate.of(2026, 10, 2), note = null),
            // Aún no ha llegado el 2 de octubre a las 18:05
            aMovement(id = "seguro", amountCents = 10_000, category = MovementCategory.BILLS, date = LocalDate.of(2026, 10, 28), note = "Seguro")
        )
    )

    @Test
    fun `el resumen lleva totales, balance coloreado y tasa de ahorro`() {
        val statement = monthStatement(october, issuedAt)

        assertEquals("Resumen de octubre de 2026", statement.subtitle)
        val rows = statement.rows()
        assertTrue(rows.contains("Ingresos" to "+2.000,00$nbsp€"))
        assertTrue(rows.contains("Gastos" to "−1.000,00$nbsp€"))
        assertTrue(rows.contains("Tasa de ahorro" to "50 %"))
        assertEquals(Total("BALANCE", "+1.000,00$nbsp€", MovementType.INCOME), statement.blocks.filterIsInstance<Total>().single())
    }

    @Test
    fun `el desglose por categoria va de mayor a menor con su porcentaje`() {
        val statement = monthStatement(october, issuedAt)
        val blocks = statement.blocks
        val expensesStart = blocks.indexOf(Heading("GASTOS POR CATEGORÍA"))

        assertEquals(
            listOf(
                Row("Vivienda", "750,00$nbsp€ · 75 %"),
                Row("Supermercado", "150,00$nbsp€ · 15 %"),
                Row("Facturas", "100,00$nbsp€ · 10 %")
            ),
            blocks.subList(expensesStart + 1, expensesStart + 4)
        )
        assertTrue(blocks.contains(Heading("INGRESOS POR CATEGORÍA")))
        assertTrue(blocks.contains(Row("Nómina", "2.000,00$nbsp€ · 100 %")))
    }

    @Test
    fun `los movimientos van en orden de fecha y los previstos se marcan`() {
        val statement = monthStatement(october, issuedAt)
        val blocks = statement.blocks
        val listStart = blocks.indexOf(Heading("MOVIMIENTOS (4)"))
        val labels = blocks.drop(listStart + 1).filterIsInstance<Row>().map { it.label }

        assertEquals(listOf("01/10 Nómina", "01/10 Alquiler", "02/10 Supermercado", "28/10 Seguro *"), labels)
        assertTrue(statement.footer.contains("* Previsto: aún no ha llegado."))
    }

    @Test
    fun `sin ingresos no hay desglose de ingresos ni tasa de ahorro`() {
        val onlyExpenses = monthMovements(YearMonth.of(2026, 10), listOf(aMovement(date = LocalDate.of(2026, 10, 1))))
        val statement = monthStatement(onlyExpenses, issuedAt)

        assertFalse(statement.blocks.contains(Heading("INGRESOS POR CATEGORÍA")))
        assertFalse(statement.rows().any { it.first == "Tasa de ahorro" })
        assertEquals(MovementType.EXPENSE, statement.blocks.filterIsInstance<Total>().single().type)
    }

    @Test
    fun `el pie de foto del resumen dice el mes, los totales y el balance`() {
        assertEquals(
            "Resumen de octubre de 2026\nIngresos +2.000,00$nbsp€ · Gastos −1.000,00$nbsp€\nBalance +1.000,00$nbsp€ (ahorro del 50 %)",
            monthShareText(october)
        )
    }

    // endregion
}
