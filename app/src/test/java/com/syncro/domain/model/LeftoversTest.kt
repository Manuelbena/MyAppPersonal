package com.syncro.domain.model

import com.syncro.testutil.DAY
import com.syncro.testutil.aTask
import com.syncro.testutil.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

/**
 * Qué tareas cuentan como pendientes en cada momento del día.
 * Responsabilidades: antes de las 21:00 solo las de días anteriores (hoy aún hay tiempo) y
 * propuestas "a hoy"; desde las 21:00 también las de hoy y propuestas "a mañana"; el repaso de
 * la mañana es el mismo que el de la noche anterior. Riesgos: el límite exacto de las 21:00,
 * colar tareas hechas o futuras, y formatear mal las atrasadas.
 */
class LeftoversTest {

    private val yesterday = aTask(id = "ayer", date = DAY.minusDays(1))
    private val today = aTask(id = "hoy", date = DAY)
    private val done = aTask(id = "hecha", date = DAY.minusDays(1), isCompleted = true)
    private val all = listOf(yesterday, today, done)

    @Test
    fun `por la manana solo cuentan las de dias anteriores y se proponen para hoy`() {
        val leftovers = leftoverTasks(all, DAY.atTime(9, 0))

        assertEquals(listOf("ayer"), leftovers.tasks.map { it.id })
        assertEquals(MoveTarget.TODAY, leftovers.target)
        assertEquals(DAY.minusDays(1), leftovers.reviewDate)
    }

    @Test
    fun `desde las 21 00 cuentan tambien las de hoy y se proponen para manana`() {
        val leftovers = leftoverTasks(all, DAY.atTime(21, 0))

        assertEquals(listOf("ayer", "hoy"), leftovers.tasks.map { it.id })
        assertEquals(MoveTarget.TOMORROW, leftovers.target)
        assertEquals(DAY, leftovers.reviewDate)
    }

    @Test
    fun `a las 20 59 todavia es el repaso de la manana`() {
        assertEquals(MoveTarget.TODAY, leftoverTasks(all, DAY.atTime(20, 59)).target)
    }

    @Test
    fun `el repaso de la noche y el de la manana siguiente son el mismo`() {
        val night = leftoverTasks(all, DAY.atTime(22, 0))
        val nextMorning = leftoverTasks(all, DAY.plusDays(1).atTime(9, 0))

        assertEquals(night.reviewDate, nextMorning.reviewDate)
    }

    @Test
    fun `si el usuario adelanta la noche a las 20 00 el repaso empieza a esa hora`() {
        val leftovers = leftoverTasks(all, DAY.atTime(20, 0), eveningFrom = at("20:00"))

        assertEquals(MoveTarget.TOMORROW, leftovers.target)
        assertEquals(listOf("ayer", "hoy"), leftovers.tasks.map { it.id })
    }

    @Test
    fun `las tareas futuras no cuentan`() {
        val tomorrow = aTask(date = DAY.plusDays(1))

        assert(leftoverTasks(listOf(tomorrow), DAY.atTime(22, 0)).tasks.isEmpty())
    }

    @Test
    fun `en la lista una tarea del dia muestra la hora y una atrasada tambien el dia`() {
        assertEquals("17:00", aTask(date = DAY, time = at("17:00")).toChatTask(DAY).detail)
        assertEquals("jue 24 · 17:00", aTask(date = DAY.minusDays(2), time = at("17:00")).toChatTask(DAY).detail)
        assertNull(aTask(date = DAY, time = LocalTime.MIDNIGHT).toChatTask(DAY).detail)
    }

    @Test
    fun `pasar a hoy o a manana calcula el dia desde hoy`() {
        assertEquals(DAY, MoveTarget.TODAY.dateFrom(DAY))
        assertEquals(DAY.plusDays(1), MoveTarget.TOMORROW.dateFrom(DAY))
    }
}
