package com.syncro.domain.model

import com.syncro.domain.usecase.GetTimelineUseCase
import com.syncro.domain.usecase.GetTodayAgendaUseCase
import com.syncro.testutil.DAY
import com.syncro.testutil.FakeEventRepository
import com.syncro.testutil.FakeTaskRepository
import com.syncro.testutil.aTask
import com.syncro.testutil.anEvent
import com.syncro.testutil.at
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Lo que queda de hoy (el widget "Tu día").
 * Riesgos: enseñar lo ya hecho o terminado, esconder tareas atrasadas que siguen por hacer, perder
 * los eventos de todo el día y tomar la hora del sistema en vez del reloj inyectado.
 */
class TodayAgendaTest {

    private val now = DAY.atTime(12, 0)

    @Test
    fun `quedan las tareas sin hacer aunque su hora ya haya pasado`() {
        val agenda = todayAgenda(
            listOf(aTask(id = "pasada", time = at("09:00")), aTask(id = "hecha", isCompleted = true), aTask(id = "luego", time = at("18:00"))),
            now
        )

        assertEquals(listOf("pasada", "luego"), agenda.items.map { (it as SyncroItem.Task).id })
    }

    @Test
    fun `los eventos terminados o completados desaparecen`() {
        val agenda = todayAgenda(
            listOf(
                anEvent(id = "terminado", startTime = at("09:00"), endTime = at("10:00")),
                anEvent(id = "en-curso", startTime = at("11:30"), endTime = at("13:00")),
                anEvent(id = "completado", startTime = at("15:00"), endTime = at("16:00")).copy(isCompleted = true),
                anEvent(id = "todo-el-dia", startTime = LocalTime.MIDNIGHT, endTime = LocalTime.MIDNIGHT)
            ),
            now
        )

        assertEquals(listOf("en-curso", "todo-el-dia"), agenda.items.map { (it as SyncroItem.Event).id })
    }

    @Test
    fun `un evento que termina manana sigue aunque hoy ya sea tarde`() {
        val night = anEvent(id = "noche", startTime = at("21:30"), endTime = at("01:00")).copy(endDate = DAY.plusDays(1))

        assertTrue(todayAgenda(listOf(night), DAY.atTime(23, 30)).items.isNotEmpty())
    }

    @Test
    fun `el caso de uso usa el reloj inyectado`() = runTest {
        val tasks = FakeTaskRepository().apply { insertTask(aTask(id = "t1")) }
        val clock = Clock.fixed(DAY.atTime(8, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

        val agenda = GetTodayAgendaUseCase(GetTimelineUseCase(tasks, FakeEventRepository()), clock)()

        assertEquals(DAY, agenda.date)
        assertEquals(1, agenda.items.size)
    }
}
