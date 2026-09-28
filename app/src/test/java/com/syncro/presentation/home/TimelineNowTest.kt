package com.syncro.presentation.home

import com.syncro.testutil.DAY
import com.syncro.testutil.aNote
import com.syncro.testutil.aTask
import com.syncro.testutil.anEvent
import com.syncro.testutil.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

/**
 * Reglas del "ahora" en el timeline: progreso del raíl, minutos restantes y posición de la línea
 * "Ahora". Riesgos: eventos que cruzan la medianoche, los de todo el día y los bordes exactos.
 */
class TimelineNowTest {

    private val meeting = anEvent(date = DAY, startTime = at("10:00"), endTime = at("12:00"))

    @Test
    fun `el progreso va de 0 antes de empezar a 1 al terminar`() {
        assertEquals(0f, meeting.progressAt(DAY.atTime(9, 0)))
        assertEquals(0f, meeting.progressAt(DAY.atTime(10, 0)))
        assertEquals(0.25f, meeting.progressAt(DAY.atTime(10, 30)))
        assertEquals(1f, meeting.progressAt(DAY.atTime(12, 0)))
        assertEquals(1f, meeting.progressAt(DAY.plusDays(1).atTime(8, 0)))
    }

    @Test
    fun `un evento que cruza la medianoche avanza entre los dos dias`() {
        val dinner = anEvent(date = DAY, endDate = DAY.plusDays(1), startTime = at("22:00"), endTime = at("02:00"))

        assertEquals(0.5f, dinner.progressAt(DAY.plusDays(1).atTime(0, 0)))
        assertEquals(60L, dinner.minutesLeftAt(DAY.plusDays(1).atTime(1, 0)))
    }

    @Test
    fun `minutos restantes solo mientras esta en curso`() {
        assertNull(meeting.minutesLeftAt(DAY.atTime(9, 59)))
        assertEquals(90L, meeting.minutesLeftAt(DAY.atTime(10, 30)))
        // Con segundos por delante aún queda 1 minuto, no 0
        assertEquals(1L, meeting.minutesLeftAt(DAY.atTime(11, 59, 30)))
        assertNull(meeting.minutesLeftAt(DAY.atTime(12, 0)))
        val allDay = anEvent(startTime = LocalTime.MIDNIGHT, endTime = LocalTime.MIDNIGHT)
        assertNull(allDay.minutesLeftAt(DAY.atTime(12, 0)))
    }

    @Test
    fun `la linea Ahora va antes del primer elemento que empieza despues`() {
        val items = listOf(
            anEvent(title = "Todo el día", startTime = LocalTime.MIDNIGHT, endTime = LocalTime.MIDNIGHT),
            anEvent(title = "Reunión", startTime = at("10:00"), endTime = at("11:00")),
            aTask(title = "Llamar", time = at("12:30")),
            anEvent(title = "Cena", startTime = at("21:00"), endTime = at("22:00"))
        )

        assertEquals(1, nowIndicatorIndex(items, DAY, DAY.atTime(8, 0)))
        assertEquals(2, nowIndicatorIndex(items, DAY, DAY.atTime(12, 0)))
        // Si ya empezó todo, al final
        assertEquals(4, nowIndicatorIndex(items, DAY, DAY.atTime(23, 0)))
    }

    @Test
    fun `sin linea Ahora si el dia no es hoy o no hay nada`() {
        val items = listOf(anEvent(startTime = at("10:00"), endTime = at("11:00")))

        assertNull(nowIndicatorIndex(items, DAY.plusDays(1), DAY.atTime(8, 0)))
        assertNull(nowIndicatorIndex(emptyList(), DAY, DAY.atTime(8, 0)))
        assertNull(nowIndicatorIndex(listOf(aNote()), DAY, DAY.atTime(8, 0)))
    }

    @Test
    fun `un evento que viene del dia anterior cuenta como ya empezado`() {
        val fromYesterday = anEvent(date = DAY.minusDays(1), endDate = DAY, startTime = at("23:00"), endTime = at("09:00"))
        val later = anEvent(startTime = at("10:00"), endTime = at("11:00"))

        assertEquals(1, nowIndicatorIndex(listOf(fromYesterday, later), DAY, DAY.atTime(0, 30)))
    }
}
