package com.syncro.presentation.components

import com.syncro.testutil.DAY
import com.syncro.testutil.anEvent
import com.syncro.testutil.at
import org.junit.Assert.assertEquals
import org.junit.Test

/** Texto que se envía al compartir un evento (DAY es sábado 26 de septiembre de 2026). */
class ShareEventTest {

    @Test
    fun `evento normal con ubicacion y descripcion`() {
        val event = anEvent(title = "Cena", startTime = at("21:00"), endTime = at("23:00"), location = "Casa de Ana", description = "Llevar postre")

        assertEquals("Cena\nsáb 26 sept, 21:00 – 23:00\nUbicación: Casa de Ana\n\nLlevar postre", event.toShareText())
    }

    @Test
    fun `evento que termina al dia siguiente indica el dia de fin`() {
        val event = anEvent(title = "Cena", endDate = DAY.plusDays(1), startTime = at("21:30"), endTime = at("01:00"), description = null)

        assertEquals("Cena\nsáb 26 sept, 21:30 – 01:00 (dom 27 sept)", event.toShareText())
    }

    @Test
    fun `evento de dia completo no muestra horas`() {
        val event = anEvent(title = "Festivo", startTime = at("00:00"), endTime = at("00:00"), description = null)

        assertEquals("Festivo\nsáb 26 sept, todo el día", event.toShareText())
    }

    @Test
    fun `sin ubicacion ni descripcion no deja lineas vacias`() {
        val event = anEvent(title = "Café", startTime = at("10:00"), endTime = at("10:30"), location = "  ", description = "")

        assertEquals("Café\nsáb 26 sept, 10:00 – 10:30", event.toShareText())
    }
}
