package com.syncro.presentation.savings

import com.syncro.domain.model.MovementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * La imagen del ticket: siempre del mismo ancho y con el alto que pida el contenido, para que un
 * concepto largo no se corte.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TicketImageTest {

    private fun ticket(note: String) = TicketContent(
        title = "SYNCRO",
        subtitle = "Justificante de gasto",
        blocks = listOf(
            TicketBlock.Rule,
            TicketBlock.Row("Fecha", "01/10/2026"),
            TicketBlock.Row("Concepto", note),
            TicketBlock.Rule,
            TicketBlock.Total("TOTAL", "−45,90 €", MovementType.EXPENSE)
        ),
        footer = listOf("Ref. ABC", "Emitido el 02/10/2026 18:05")
    )

    @Test
    fun `la imagen tiene el ancho fijo y el papel es blanco`() {
        val bitmap = renderTicket(ticket("Mercadona"))

        assertEquals(1080, bitmap.width)
        // En el centro, a la altura del título, está el papel (blanco o el texto encima)
        assertEquals(0xFFFFFFFF.toInt(), bitmap.getPixel(60, bitmap.height / 2))
    }

    @Test
    fun `un concepto largo hace el ticket mas alto en vez de cortarse`() {
        val short = renderTicket(ticket("Mercadona")).height
        val long = renderTicket(ticket("Compra semanal en el supermercado del barrio con la lista de toda la familia")).height

        assertTrue(long > short)
    }
}
