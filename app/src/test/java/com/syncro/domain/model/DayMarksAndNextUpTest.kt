package com.syncro.domain.model

import com.syncro.testutil.DAY
import com.syncro.testutil.aTask
import com.syncro.testutil.anEvent
import com.syncro.testutil.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Plan de pruebas de [dayMarks] (puntos de la tira de la semana) y [nextUp] ("Lo próximo").
 *
 * Riesgos: un evento de varios días marcado solo en su primer día, días fuera de la tira,
 * contar como "siguiente" un evento de mañana o uno de todo el día, y elegir mal el evento en curso.
 */
class DayMarksAndNextUpTest {

    // region dayMarks

    @Test
    fun `un dia esta pendiente si le queda algo y hecho si todo esta completado`() {
        val marks = dayMarks(
            tasks = listOf(aTask(date = DAY, isCompleted = true), aTask(date = DAY.plusDays(1))),
            events = listOf(anEvent(date = DAY, isCompleted = true)),
            from = DAY, to = DAY.plusDays(6)
        )

        assertEquals(mapOf(DAY to DayMark.DONE, DAY.plusDays(1) to DayMark.PENDING), marks)
    }

    @Test
    fun `un evento de varios dias marca todos sus dias y no los de fuera de la tira`() {
        val trip = anEvent(date = DAY, endDate = DAY.plusDays(2), startTime = at("09:00"), endTime = at("18:00"))

        val marks = dayMarks(emptyList(), listOf(trip), from = DAY.plusDays(1), to = DAY.plusDays(10))

        assertEquals(setOf(DAY.plusDays(1), DAY.plusDays(2)), marks.keys)
    }

    // endregion

    // region nextUp

    private val now = DAY.atTime(10, 30)

    @Test
    fun `muestra el evento en curso y el siguiente de hoy`() {
        val meeting = anEvent(id = "en-curso", startTime = at("10:00"), endTime = at("11:00"))
        val lunch = anEvent(id = "comida", startTime = at("14:00"), endTime = at("15:00"))
        val dentist = anEvent(id = "dentista", startTime = at("12:00"), endTime = at("13:00"))

        val next = nextUp(listOf(meeting, lunch, dentist), now)!!

        assertEquals("en-curso", next.current?.id)
        assertEquals("dentista", next.next?.id)
    }

    @Test
    fun `no cuentan los de todo el dia, los completados ni los de manana`() {
        val allDay = anEvent(startTime = at("00:00"), endTime = at("00:00"))
        val done = anEvent(startTime = at("12:00"), endTime = at("13:00"), isCompleted = true)
        val tomorrow = anEvent(date = DAY.plusDays(1), startTime = at("09:00"), endTime = at("10:00"))

        val next = nextUp(listOf(allDay, done, tomorrow, aTask(date = DAY)), now)!!

        assertNull(next.current)
        assertNull(next.next)
        assertEquals(1, next.tasksTotal)
    }

    @Test
    fun `un evento que empezo ayer y sigue cuenta como en curso`() {
        val night = anEvent(date = DAY.minusDays(1), endDate = DAY, startTime = at("22:00"), endTime = at("11:00"))

        assertEquals(night, nextUp(listOf(night), now)?.current)
    }

    @Test
    fun `cuenta las tareas hechas del dia`() {
        val next = nextUp(listOf(aTask(date = DAY, isCompleted = true), aTask(date = DAY), aTask(date = DAY)), now)!!

        assertEquals(1, next.tasksDone)
        assertEquals(3, next.tasksTotal)
    }

    @Test
    fun `sin eventos pendientes ni tareas no hay nada que mostrar`() {
        assertNull(nextUp(listOf(anEvent(startTime = at("08:00"), endTime = at("09:00"))), now))
    }

    // endregion
}
