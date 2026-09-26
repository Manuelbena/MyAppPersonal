package com.syncro.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * Valores límite de [isValidEventTimeRange]: la frontera está en "fin == inicio", así que se
 * prueba justo antes, justo en ella y justo después, más los extremos del día.
 */
class ValidationTest {

    @Test
    fun `fin un minuto antes del inicio es invalido`() {
        assertFalse(isValidEventTimeRange(start = time("10:00"), end = time("09:59")))
    }

    @Test
    fun `fin igual al inicio es valido`() {
        assertTrue(isValidEventTimeRange(start = time("10:00"), end = time("10:00")))
    }

    @Test
    fun `fin un minuto despues del inicio es valido`() {
        assertTrue(isValidEventTimeRange(start = time("10:00"), end = time("10:01")))
    }

    @Test
    fun `dia completo 00-00 a 00-00 es valido`() {
        assertTrue(isValidEventTimeRange(start = time("00:00"), end = time("00:00")))
    }

    @Test
    fun `de 00-00 a 23-59 es valido`() {
        assertTrue(isValidEventTimeRange(start = time("00:00"), end = time("23:59")))
    }

    @Test
    fun `cruzar la medianoche es invalido porque un evento ocupa un solo dia`() {
        // Caso real: "Cenar con mi amigo" de 21:30 a 01:00 quedó atascado sin subir a Google
        assertFalse(isValidEventTimeRange(start = time("21:30"), end = time("01:00")))
    }

    private fun time(value: String) = LocalTime.parse(value)
}
