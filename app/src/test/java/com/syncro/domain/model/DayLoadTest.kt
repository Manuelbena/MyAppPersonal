package com.syncro.domain.model

import com.syncro.testutil.DAY
import com.syncro.testutil.anEvent
import com.syncro.testutil.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayLoadTest {

    @Test
    fun `detecta los eventos que se pisan y el rato en que coinciden`() {
        val events = listOf(
            anEvent(title = "Reunión", startTime = at("10:30"), endTime = at("11:30")),
            anEvent(title = "Dentista", startTime = at("10:00"), endTime = at("11:00")),
            anEvent(title = "Comida", startTime = at("14:00"), endTime = at("15:00"))
        )

        val load = dayLoadOf(events, DAY, today = DAY)!!

        assertEquals(listOf(EventClash("Dentista", "Reunión", at("10:30"), at("11:00"))), load.clashes)
        assertFalse(load.isTomorrow)
        assertFalse(load.isOverloaded)
    }

    @Test
    fun `uno que empieza justo cuando acaba otro no se pisa`() {
        val events = listOf(
            anEvent(title = "Dentista", startTime = at("10:00"), endTime = at("11:00")),
            anEvent(title = "Reunión", startTime = at("11:00"), endTime = at("12:00"))
        )

        assertNull(dayLoadOf(events, DAY, today = DAY))
    }

    @Test
    fun `no cuentan los de todo el dia, los completados ni el mismo evento repetido`() {
        val events = listOf(
            anEvent(title = "Festivo", startTime = at("00:00"), endTime = at("00:00")),
            anEvent(title = "Dentista", startTime = at("10:00"), endTime = at("11:00")),
            anEvent(title = "Dentista", startTime = at("10:00"), endTime = at("11:00")),
            anEvent(title = "Café", startTime = at("10:30"), endTime = at("11:00"), isCompleted = true)
        )

        assertNull(dayLoadOf(events, DAY, today = DAY))
    }

    @Test
    fun `un evento que viene de la noche anterior se pisa solo con su parte del dia`() {
        val events = listOf(
            anEvent(title = "Guardia", date = DAY.minusDays(1), endDate = DAY, startTime = at("22:00"), endTime = at("08:00")),
            anEvent(title = "Desayuno", startTime = at("07:30"), endTime = at("08:30"))
        )

        val load = dayLoadOf(events, DAY, today = DAY)!!

        assertEquals(listOf(EventClash("Guardia", "Desayuno", at("07:30"), at("08:00"))), load.clashes)
        assertEquals(at("00:00"), load.firstStart)
    }

    @Test
    fun `va a tope con muchas horas ocupadas, contando los solapes una vez`() {
        val events = listOf(
            anEvent(title = "Trabajo", startTime = at("08:00"), endTime = at("17:00")),
            anEvent(title = "Llamada", startTime = at("16:00"), endTime = at("16:30")),
            anEvent(title = "Inglés", startTime = at("18:00"), endTime = at("19:30"))
        )

        val load = dayLoadOf(events, DAY, today = DAY.minusDays(1))!!

        assertTrue(load.isTomorrow)
        assertTrue(load.isOverloaded)
        assertEquals(10 * 60 + 30L, load.busyMinutes)
        assertEquals(at("08:00"), load.firstStart)
        assertEquals(at("19:30"), load.lastEnd)
    }

    @Test
    fun `va a tope con muchos eventos aunque sean cortos`() {
        val events = (9..14).map { anEvent(title = "Cita $it", startTime = at("%02d:00".format(it)), endTime = at("%02d:30".format(it))) }

        assertTrue(dayLoadOf(events, DAY, today = DAY)!!.isOverloaded)
    }

    @Test
    fun `una jornada normal no avisa`() {
        val events = listOf(anEvent(title = "Trabajo", startTime = at("09:00"), endTime = at("17:00")))

        assertNull(dayLoadOf(events, DAY, today = DAY))
    }

    @Test
    fun `el asistente avisa de un solape`() {
        val load = DayLoad(DAY, isTomorrow = true, listOf(EventClash("Dentista", "Reunión", at("10:30"), at("11:00"))), 2, 90, at("10:00"), at("11:30"))

        val message = assistantConversation(answer = DigestAnswer.ACCEPTED, notificationsAllowed = true, dayLoads = listOf(load))
            .messages.single { it.id == "day-load-$DAY" }

        assertEquals(
            "⚠️ Mañana se te pisan «Dentista» y «Reunión» (de 10:30 a 11:00).\n¿Te da tiempo a todo o mueves alguno? 🗓️",
            message.text
        )
    }

    @Test
    fun `el asistente avisa de varios solapes y de que el dia va a tope`() {
        val load = DayLoad(
            DAY,
            isTomorrow = false,
            clashes = listOf(
                EventClash("Dentista", "Reunión", at("10:30"), at("11:00")),
                EventClash("Cena", "Cine", at("21:00"), at("00:00"))
            ),
            eventCount = 7,
            busyMinutes = 11 * 60,
            firstStart = at("08:00"),
            lastEnd = at("00:00")
        )

        val message = assistantConversation(answer = DigestAnswer.ACCEPTED, notificationsAllowed = true, dayLoads = listOf(load))
            .messages.single { it.id == "day-load-$DAY" }

        assertEquals(
            "⚠️ Hoy se te pisan 2 veces:\n" +
                "• «Dentista» y «Reunión», de 10:30 a 11:00\n" +
                "• «Cena» y «Cine», de 21:00 a 24:00\n" +
                "¿Te da tiempo a todo o mueves alguno? 🗓️\n\n" +
                "🔋 Y vas a tope: 7 eventos y 11 h ocupadas, de 8:00 a 24:00. Guárdate un rato para comer y respirar. 🌿",
            message.text
        )
    }

    @Test
    fun `el aviso de manana va detras de lo de hoy`() {
        val tomorrow = DayLoad(DAY.plusDays(1), isTomorrow = true, emptyList(), 6, 8 * 60, at("09:00"), at("20:00"))

        val messages = assistantConversation(
            answer = DigestAnswer.ACCEPTED,
            notificationsAllowed = true,
            errands = ErrandsDay(DAY, listOf("Correos", "Banco")),
            dayLoads = listOf(tomorrow)
        ).messages

        assertEquals("day-load-${DAY.plusDays(1)}", messages.last().id)
        assertEquals(
            "🔋 Mañana vas a tope: 6 eventos y 8 h ocupadas, de 9:00 a 20:00. Guárdate un rato para comer y respirar. 🌿",
            messages.last().text
        )
    }
}
