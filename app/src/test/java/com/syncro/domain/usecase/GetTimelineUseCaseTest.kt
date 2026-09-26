package com.syncro.domain.usecase

import com.syncro.domain.model.SyncroItem
import com.syncro.testutil.DAY
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
        tasks.insertTask(aTask(title = "Tarea 18:00", time = "18:00"))
        tasks.insertTask(aTask(title = "Tarea 08:00", time = "08:00"))
        events.insertEvent(anEvent(title = "Evento 12:30", startTime = "12:30", endTime = "13:00"))
        events.insertEvent(anEvent(title = "Evento 09:15", startTime = "09:15", endTime = "10:00"))

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

    private fun SyncroItem.title() = when (this) {
        is SyncroItem.Task -> title
        is SyncroItem.Event -> title
        is SyncroItem.Note -> title
    }
}
