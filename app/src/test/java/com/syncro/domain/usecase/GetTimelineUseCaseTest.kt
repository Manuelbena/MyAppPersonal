package com.syncro.domain.usecase

import com.syncro.domain.model.SyncroItem
import com.syncro.testutil.DAY
import com.syncro.testutil.at
import com.syncro.testutil.FakeEventRepository
import com.syncro.testutil.FakeTaskRepository
import com.syncro.testutil.aTask
import com.syncro.testutil.anEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [GetTimelineUseCase] combina tareas y eventos de un día en una lista ordenada por hora y
 * debe reaccionar a cambios en cualquiera de los dos repositorios.
 */
class GetTimelineUseCaseTest {

    private lateinit var tasks: FakeTaskRepository
    private lateinit var events: FakeEventRepository
    private lateinit var getTimeline: GetTimelineUseCase

    @Before
    fun setUp() {
        tasks = FakeTaskRepository()
        events = FakeEventRepository()
        getTimeline = GetTimelineUseCase(tasks, events)
    }

    @Test
    fun `un dia sin nada devuelve una lista vacia`() = runTest {
        assertTrue(getTimeline(DAY).first().isEmpty())
    }

    @Test
    fun `mezcla tareas y eventos ordenados por hora`() = runTest {
        tasks.insertTask(aTask(title = "Tarea 18:00", time = at("18:00")))
        tasks.insertTask(aTask(title = "Tarea 08:00", time = at("08:00")))
        events.insertEvent(anEvent(title = "Evento 12:30", startTime = at("12:30"), endTime = at("13:00")))
        events.insertEvent(anEvent(title = "Evento 09:15", startTime = at("09:15"), endTime = at("10:00")))

        val titles = getTimeline(DAY).first().map { it.title() }

        assertEquals(listOf("Tarea 08:00", "Evento 09:15", "Evento 12:30", "Tarea 18:00"), titles)
    }

    @Test
    fun `solo incluye elementos del dia pedido`() = runTest {
        tasks.insertTask(aTask(title = "Ayer", date = DAY.minusDays(1)))
        events.insertEvent(anEvent(title = "Mañana", date = DAY.plusDays(1)))
        tasks.insertTask(aTask(title = "Hoy"))

        assertEquals(listOf("Hoy"), getTimeline(DAY).first().map { it.title() })
    }

    @Test
    fun `se actualiza cuando cambia cualquiera de los dos repositorios`() = runTest {
        val timeline = getTimeline(DAY)
        assertEquals(0, timeline.first().size)

        events.insertEvent(anEvent())
        assertEquals(1, timeline.first().size)

        tasks.insertTask(aTask())
        assertEquals(2, timeline.first().size)
    }

    @Test
    fun `un evento que viene del dia anterior aparece el primero del dia siguiente`() = runTest {
        events.insertEvent(anEvent(title = "Cena", date = DAY.minusDays(1), endDate = DAY, startTime = at("21:30"), endTime = at("01:00")))
        tasks.insertTask(aTask(title = "Desayuno", time = at("08:00")))

        assertEquals(listOf("Cena", "Desayuno"), getTimeline(DAY).first().map { it.title() })
    }

    @Test
    fun `un evento que termina justo a medianoche no aparece al dia siguiente`() = runTest {
        events.insertEvent(anEvent(title = "Hasta las 00:00", date = DAY.minusDays(1), endDate = DAY, startTime = at("22:00"), endTime = at("00:00")))

        assertTrue(getTimeline(DAY).first().isEmpty())
    }

    @Test
    fun `lo que es de todo el dia va siempre primero`() = runTest {
        tasks.insertTask(aTask(title = "Temprano", time = at("06:00")))
        events.insertEvent(anEvent(title = "Viene de ayer", date = DAY.minusDays(1), endDate = DAY, startTime = at("22:00"), endTime = at("02:00")))
        events.insertEvent(anEvent(title = "Festivo", startTime = at("00:00"), endTime = at("00:00")))
        tasks.insertTask(aTask(title = "Tomar creatina", time = at("00:00")))

        val titles = getTimeline(DAY).first().map { it.title() }

        // Primero todo el día (tarea y evento), luego lo que viene de ayer y luego por hora
        assertEquals(setOf("Festivo", "Tomar creatina"), titles.take(2).toSet())
        assertEquals(listOf("Viene de ayer", "Temprano"), titles.drop(2))
    }

    private fun SyncroItem.title() = when (this) {
        is SyncroItem.Task -> title
        is SyncroItem.Event -> title
        is SyncroItem.Note -> title
    }
}
