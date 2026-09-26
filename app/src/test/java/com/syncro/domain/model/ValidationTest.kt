package com.syncro.domain.model

import com.syncro.testutil.DAY
import com.syncro.testutil.at
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Valores límite de [isValidEventRange]: la frontera está en "fin == inicio", así que se prueba
 * justo antes, justo en ella y justo después, más los cambios de día.
 */
class ValidationTest {

    @Test
    fun `fin un minuto antes del inicio es invalido`() {
        assertFalse(isValidEventRange(start = on(DAY, "10:00"), end = on(DAY, "09:59")))
    }

    @Test
    fun `fin igual al inicio es valido`() {
        assertTrue(isValidEventRange(start = on(DAY, "10:00"), end = on(DAY, "10:00")))
    }

    @Test
    fun `fin un minuto despues del inicio es valido`() {
        assertTrue(isValidEventRange(start = on(DAY, "10:00"), end = on(DAY, "10:01")))
    }

    @Test
    fun `dia completo 00-00 a 00-00 es valido`() {
        assertTrue(isValidEventRange(start = on(DAY, "00:00"), end = on(DAY, "00:00")))
    }

    @Test
    fun `cruzar la medianoche terminando al dia siguiente es valido`() {
        // Caso real: "Cenar con mi amigo" de 21:30 a 01:00. Antes no se podía representar
        assertTrue(isValidEventRange(start = on(DAY, "21:30"), end = on(DAY.plusDays(1), "01:00")))
    }

    @Test
    fun `21-30 a 01-00 del mismo dia sigue siendo invalido`() {
        assertFalse(isValidEventRange(start = on(DAY, "21:30"), end = on(DAY, "01:00")))
    }

    @Test
    fun `terminar un dia antes de empezar es invalido aunque la hora sea posterior`() {
        assertFalse(isValidEventRange(start = on(DAY, "09:00"), end = on(DAY.minusDays(1), "18:00")))
    }

    private fun on(date: LocalDate, time: String): LocalDateTime = date.atTime(at(time))
}
