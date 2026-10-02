package com.syncro.presentation.savings

import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.MovementOccurrence
import com.syncro.domain.model.MovementType
import com.syncro.testutil.aMovement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Plan: el ticket que se imprime. Riesgos: que falte un dato del movimiento, que un concepto con
 * "<" o "&" rompa el HTML (o inyecte etiquetas) y que parezca un documento oficial.
 */
class MovementTicketTest {

    private val printedAt = LocalDateTime.of(2026, 10, 2, 18, 5)

    private fun ticketFor(note: String?, repeats: Boolean = false, type: MovementType = MovementType.EXPENSE) =
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

    @Test
    fun `el ticket lleva fecha del mes, tipo, categoria, concepto, total y referencia`() {
        val html = movementTicketHtml(ticketFor("Mercadona", repeats = true), printedAt)

        // La fecha es la del mes que se mira (los mensuales caen cada mes)
        assertTrue(html.contains("01/10/2026"))
        assertTrue(html.contains("Justificante de gasto"))
        assertTrue(html.contains("Supermercado"))
        assertTrue(html.contains("Mercadona"))
        assertTrue(html.contains("Mensual"))
        assertTrue(html.contains("−45,90"))
        assertTrue(html.contains("Ref. 3F2A9C1B"))
        assertTrue(html.contains("Emitido el 02/10/2026 18:05"))
    }

    @Test
    fun `deja claro que no es una factura ni un justificante bancario`() {
        assertTrue(movementTicketHtml(ticketFor(null), printedAt).contains("No es una factura ni un justificante bancario"))
    }

    @Test
    fun `un concepto con simbolos no rompe el HTML`() {
        val html = movementTicketHtml(ticketFor("<b>Tom & Jerry</b>"), printedAt)

        assertTrue(html.contains("&lt;b&gt;Tom &amp; Jerry&lt;/b&gt;"))
        assertFalse(html.contains("<b>Tom"))
    }

    @Test
    fun `sin concepto ni repeticion no salen esas filas`() {
        val html = movementTicketHtml(ticketFor(null), printedAt)

        assertFalse(html.contains("Concepto"))
        assertFalse(html.contains("Frecuencia"))
    }

    @Test
    fun `el trabajo de impresion se llama por tipo y dia`() {
        assertEquals("Syncro - Ingreso 01-10-2026", ticketJobName(ticketFor(null, type = MovementType.INCOME)))
    }
}
