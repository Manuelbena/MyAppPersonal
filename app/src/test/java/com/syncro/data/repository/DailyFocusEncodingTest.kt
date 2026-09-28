package com.syncro.data.repository

import com.syncro.domain.model.DailyFocus
import com.syncro.domain.model.FocusedTask
import com.syncro.testutil.DAY
import org.junit.Assert.assertEquals
import org.junit.Test

/** Cómo se guardan las prioridades en DataStore: lo que se escribe debe leerse igual. */
class DailyFocusEncodingTest {

    @Test
    fun `las prioridades se leen igual que se guardaron, con titulos con simbolos`() {
        val focus = DailyFocus(
            DAY,
            listOf(FocusedTask("a-1", "Llamar al banco; pedir cita"), FocusedTask("b-2", "Comprar: pan | leche"))
        )

        assertEquals(focus, decodeFocus(DAY, encodeFocus(focus)))
    }

    @Test
    fun `hoy no se guarda vacio y se lee como saltado`() {
        val skipped = DailyFocus(DAY, emptyList())

        assertEquals("", encodeFocus(skipped))
        assertEquals(skipped, decodeFocus(DAY, ""))
    }
}
