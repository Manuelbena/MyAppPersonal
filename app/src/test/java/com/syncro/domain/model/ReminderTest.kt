package com.syncro.domain.model

import com.syncro.testutil.DAY
import com.syncro.testutil.aTask
import com.syncro.testutil.anEvent
import com.syncro.testutil.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/**
 * Plan: los avisos de tareas y eventos. Responsabilidades: cuándo suena cada aviso (antes de
 * empezar, o a una hora del día en los de todo el día), cuál es el siguiente, cuáles tocan al
 * sonar la alarma y qué dice la notificación. Riesgos: avisar de algo completado, perder un aviso
 * que coincide con otro, avisar de cosas de hace días tras tener el móvil apagado, y un texto que
 * dice "mañana" cuando es hoy.
 */
class ReminderTest {

    // DAY es el sábado 26 de septiembre de 2026
    private fun on(hour: Int, minute: Int = 0, day: java.time.LocalDate = DAY) = day.atTime(hour, minute)

    private fun reminder(id: String, at: LocalDateTime, start: LocalDateTime = at, isTask: Boolean = false, isAllDay: Boolean = false, location: String? = null) =
        DueReminder(id, isTask, "Título", at, start, isAllDay, location, null)

    // region Cuándo suena

    @Test
    fun `un evento con aviso de 15 minutos suena 15 minutos antes de empezar`() {
        val event = anEvent(date = DAY, startTime = at("19:00")).copy(reminderMinutes = 15)

        assertEquals(on(18, 45), event.reminderAt())
    }

    @Test
    fun `en una tarea, ese dia a las 9 y el dia antes a las 20`() {
        val task = aTask(date = DAY, time = at("00:00"))

        assertEquals(on(9), task.copy(reminderMinutes = -9 * 60).reminderAt())
        assertEquals(on(20, day = DAY.minusDays(1)), task.copy(reminderMinutes = 4 * 60).reminderAt())
    }

    @Test
    fun `sin aviso no suena`() {
        assertNull(anEvent().reminderAt())
    }

    @Test
    fun `una hora elegida se guarda como minutos antes de empezar`() {
        assertEquals(90, reminderMinutesFor(start = on(19), at = on(17, 30)))
        assertEquals(on(17, 30), anEvent(date = DAY, startTime = at("19:00")).copy(reminderMinutes = 90).reminderAt())
    }

    @Test
    fun `cada repeticion de una serie tiene su aviso, a la misma distancia de su inicio`() {
        val template = anEvent(date = DAY, startTime = at("19:00")).copy(reminderMinutes = 30)
        val series = RepeatSeries("s", Recurrence.weeklyOn(DAY), DAY, null, DAY, template)

        val next = series.occurrence(DAY.plusWeeks(1), "e2")

        assertEquals(on(18, 30, day = DAY.plusWeeks(1)), next.reminderAt())
    }

    // endregion

    // region Cuáles tocan

    @Test
    fun `los avisos van del mas proximo al mas lejano y sin los completados`() {
        val reminders = dueReminders(
            tasks = listOf(
                aTask(id = "hecha", date = DAY, isCompleted = true).copy(reminderMinutes = 0),
                aTask(id = "sin-aviso", date = DAY)
            ),
            events = listOf(
                anEvent(id = "tarde", date = DAY, startTime = at("19:00")).copy(reminderMinutes = 15),
                anEvent(id = "manana", date = DAY, startTime = at("09:00")).copy(reminderMinutes = 5)
            )
        )

        assertEquals(listOf("manana", "tarde"), reminders.map { it.itemId })
    }

    @Test
    fun `el siguiente aviso es el primero que aun no ha sonado`() {
        val reminders = listOf(reminder("pasado", on(8)), reminder("proximo", on(10)), reminder("luego", on(12)))

        assertEquals("proximo", reminders.nextAfter(on(9))?.itemId)
        assertNull(reminders.nextAfter(on(13)))
    }

    @Test
    fun `al sonar se publican todos los que tocan desde la ultima vez, tambien si coinciden`() {
        val reminders = listOf(reminder("a", on(10)), reminder("b", on(10)), reminder("antes", on(9)), reminder("despues", on(11)))

        assertEquals(listOf("a", "b"), reminders.dueBetween(since = on(9, 30), now = on(10)).map { it.itemId })
    }

    @Test
    fun `tras tener el movil apagado no se avisa de lo de hace mas de dos horas`() {
        val reminders = listOf(reminder("viejo", on(6)), reminder("reciente", on(9)))

        assertEquals(listOf("reciente"), reminders.dueBetween(since = on(5), now = on(10)).map { it.itemId })
    }

    // endregion

    // region Texto

    @Test
    fun `el texto dice cuanto falta y donde`() {
        val gym = reminder("g", at = on(18, 45), start = on(19), location = "Gimnasio")

        assertEquals("Empieza a las 19:00 · en 15 min · 📍 Gimnasio", gym.toMessage(now = on(18, 45)).text)
        assertEquals("Empieza ahora", reminder("x", on(19)).toMessage(now = on(19)).text)
        assertEquals("Empieza a las 20:30 · en 1 h 30 min", reminder("x", on(19), start = on(20, 30)).toMessage(now = on(19)).text)
    }

    @Test
    fun `avisos de otro dia, de todo el dia y de tareas`() {
        val tomorrow = DAY.plusDays(1)

        assertEquals("Mañana a las 9:30", reminder("x", on(20), start = on(9, 30, tomorrow)).toMessage(on(20)).text)
        assertEquals("Hoy, todo el día", reminder("x", on(9), start = on(0), isAllDay = true).toMessage(on(9)).text)
        assertEquals("Tarea para mañana", reminder("x", on(20), start = on(0, day = tomorrow), isTask = true, isAllDay = true).toMessage(on(20)).text)
        assertEquals(
            "El lunes 28 de septiembre a las 10:00",
            reminder("x", on(10), start = on(10, day = DAY.plusDays(2))).toMessage(on(10)).text
        )
    }

    // endregion
}
