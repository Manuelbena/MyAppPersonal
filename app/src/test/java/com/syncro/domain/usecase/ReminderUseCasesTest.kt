package com.syncro.domain.usecase

import com.syncro.domain.model.Recurrence
import com.syncro.domain.model.RepeatFrequency
import com.syncro.testutil.DAY
import com.syncro.testutil.FakeEventRepository
import com.syncro.testutil.FakeGoogleSyncRepository
import com.syncro.testutil.FakeRepeatSeriesRepository
import com.syncro.testutil.FakeTaskRepository
import com.syncro.testutil.aTask
import com.syncro.testutil.anEvent
import com.syncro.testutil.at
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.ZoneOffset

/**
 * Plan: guardar avisos y saber cuáles quedan. Riesgos: perder el aviso al guardar, que una tarea
 * repetida tenga aviso solo en la primera, y seguir avisando de lo ya completado o borrado.
 */
class ReminderUseCasesTest {

    private val clock = Clock.fixed(DAY.atTime(9, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val tasks = FakeTaskRepository()
    private val events = FakeEventRepository()
    private val google = FakeGoogleSyncRepository()
    private val series = FakeRepeatSeriesRepository()
    private val generate = GenerateRepeatsUseCase(series, tasks, events, google, clock)
    private val observe = ObserveRemindersUseCase(tasks, events, clock)

    @Test
    fun `una tarea se guarda con su aviso`() = runTest {
        SaveTaskUseCase(tasks, google, series, generate)(title = "Llamar al banco", description = "", date = DAY, time = at("00:00"), reminderMinutes = -540)

        assertEquals(-540, tasks.tasks.value.values.single().reminderMinutes)
    }

    @Test
    fun `cada repeticion de una tarea diaria tiene su aviso`() = runTest {
        SaveTaskUseCase(tasks, google, series, generate)(
            title = "Pastilla", description = "", date = DAY, time = at("00:00"),
            repeat = Recurrence(RepeatFrequency.DAILY), reminderMinutes = -540
        )

        assertTrue(tasks.tasks.value.values.all { it.reminderMinutes == -540 })
    }

    @Test
    fun `quitar el aviso al editar un evento lo deja sin aviso`() = runTest {
        events.insertEvent(anEvent(id = "e1").copy(reminderMinutes = 15))
        val event = events.getEventById("e1")!!

        SaveEventUseCase(events, google, series, generate)(
            id = "e1", title = event.title, description = null, location = null, date = event.date,
            startTime = event.startTime, endTime = event.endTime, categoryText = event.categoryText,
            categoryColor = event.categoryColor, priority = null, subtasks = emptyList(), reminderMinutes = null
        )

        assertEquals(null, events.getEventById("e1")!!.reminderMinutes)
    }

    @Test
    fun `los avisos pendientes no incluyen lo completado ni lo que no tiene aviso`() = runTest {
        tasks.insertTask(aTask(id = "con-aviso", date = DAY).copy(reminderMinutes = 60))
        tasks.insertTask(aTask(id = "hecha", date = DAY, isCompleted = true).copy(reminderMinutes = 60))
        tasks.insertTask(aTask(id = "sin-aviso", date = DAY))
        events.insertEvent(anEvent(id = "evento", date = DAY.plusDays(3)).copy(reminderMinutes = 5))

        assertEquals(listOf("con-aviso", "evento"), observe().first().map { it.itemId })
    }

    @Test
    fun `al completar una tarea su aviso desaparece`() = runTest {
        tasks.insertTask(aTask(id = "t1", date = DAY).copy(reminderMinutes = 60))

        tasks.toggleTaskCompletion("t1")

        assertTrue(observe().first().isEmpty())
    }
}
