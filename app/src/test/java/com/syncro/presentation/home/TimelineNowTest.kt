package com.syncro.presentation.home

import com.syncro.domain.model.DayWindow
import com.syncro.domain.model.DigestSettings
import com.syncro.domain.model.dayWindow
import com.syncro.testutil.DAY
import com.syncro.testutil.anEvent
import com.syncro.testutil.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

/**
 * Reglas del "ahora" en el timeline: progreso del raíl, minutos restantes, marcas del día y altura de la línea
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
    fun `la linea Ahora se interpola entre las anclas`() {
        val anchors = listOf(TimeAnchor(9 * 60, 0f), TimeAnchor(10 * 60, 100f), TimeAnchor(12 * 60, 300f))

        assertEquals(50f, nowLineY(anchors, 9 * 60 + 30))
        // Dentro de la tarjeta del evento en curso, a la altura de su progreso
        assertEquals(200f, nowLineY(anchors, 11 * 60))
        // Antes de la primera y después de la última se queda en el borde
        assertEquals(0f, nowLineY(anchors, 6 * 60))
        assertEquals(300f, nowLineY(anchors, 23 * 60))
        assertNull(nowLineY(emptyList(), 600))
    }

    @Test
    fun `las anclas que retroceden en la hora no mueven la linea`() {
        // Reunión 10–12 y, debajo, una llamada solapada 11–11:30
        val anchors = listOf(
            TimeAnchor(10 * 60, 0f), TimeAnchor(12 * 60, 200f),
            TimeAnchor(11 * 60, 300f), TimeAnchor(11 * 60 + 30, 400f),
            TimeAnchor(13 * 60, 500f)
        )

        assertEquals(100f, nowLineY(anchors, 11 * 60))
        assertEquals(350f, nowLineY(anchors, 12 * 60 + 30))
    }

    @Test
    fun `los minutos de un evento en el dia se recortan a la medianoche`() {
        val night = anEvent(date = DAY, endDate = DAY.plusDays(1), startTime = at("22:00"), endTime = at("02:00"))

        assertEquals(22 * 60, night.startMinuteOn(DAY))
        assertEquals(MINUTES_PER_DAY, night.endMinuteOn(DAY))
        assertEquals(0, night.startMinuteOn(DAY.plusDays(1)))
        assertEquals(2 * 60, night.endMinuteOn(DAY.plusDays(1)))
    }

    @Test
    fun `las marcas del dia van arriba y abajo si todo cabe dentro`() {
        val events = listOf(
            anEvent(title = "Todo el día", startTime = LocalTime.MIDNIGHT, endTime = LocalTime.MIDNIGHT),
            anEvent(startTime = at("10:00"), endTime = at("11:00")),
            anEvent(startTime = at("18:00"), endTime = at("19:00"))
        )

        assertEquals(0 to 3, dayBoundaryIndices(events, DAY, DayWindow(at("09:00"), at("21:00"))))
    }

    @Test
    fun `los eventos fuera del dia quedan antes del inicio o despues del fin`() {
        val events = listOf(
            anEvent(startTime = at("07:00"), endTime = at("08:00")),
            anEvent(startTime = at("10:00"), endTime = at("11:00")),
            anEvent(startTime = at("22:00"), endTime = at("23:00"))
        )

        assertEquals(1 to 2, dayBoundaryIndices(events, DAY, DayWindow(at("09:00"), at("21:00"))))
    }

    @Test
    fun `sin avisos el dia va de 00 00 a 23 59`() {
        val window = DigestSettings(morningEnabled = false, eveningEnabled = false).dayWindow

        assertEquals(DayWindow(LocalTime.MIDNIGHT, at("23:59")), window)
        assertEquals(DayWindow(at("09:00"), at("21:00")), DigestSettings().dayWindow)
    }
}
