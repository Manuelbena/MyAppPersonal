package com.syncro.domain.usecase

import com.syncro.domain.model.Subtask
import com.syncro.testutil.CallLog
import com.syncro.testutil.FakeEventRepository
import com.syncro.testutil.FakeGoogleSyncRepository
import com.syncro.testutil.FakeTaskRepository
import com.syncro.testutil.aTask
import com.syncro.testutil.anEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Casos de uso de completar tarea, evento y subtarea.
 *
 * La regla común es el orden: primero se cambia en local y después se sube a Google. Al subir se
 * lee el estado local, así que si se invirtiera el orden se enviaría el estado anterior.
 */
class ToggleCompletionUseCasesTest {

    private lateinit var log: CallLog
    private lateinit var tasks: FakeTaskRepository
    private lateinit var events: FakeEventRepository
    private lateinit var google: FakeGoogleSyncRepository

    @Before
    fun setUp() {
        log = CallLog()
        tasks = FakeTaskRepository(log)
        events = FakeEventRepository(log)
        google = FakeGoogleSyncRepository(log)
    }

    @Test
    fun `completar una tarea la cambia en local y despues la sube`() = runTest {
        tasks.insertTask(aTask(id = "t1", isCompleted = false))
        log.calls.clear()

        ToggleTaskCompletionUseCase(tasks, google)("t1")

        assertTrue(tasks.tasks.value.getValue("t1").isCompleted)
        assertEquals(listOf("toggleTask(t1)", "pushTask(t1)"), log.calls)
    }

    @Test
    fun `completar un evento lo cambia en local y despues lo sube`() = runTest {
        events.insertEvent(anEvent(id = "e1", isCompleted = false))
        log.calls.clear()

        ToggleEventCompletionUseCase(events, google)("e1")

        assertTrue(events.events.value.getValue("e1").isCompleted)
        assertEquals(listOf("toggleEvent(e1)", "pushEvent(e1)"), log.calls)
    }

    @Test
    fun `completar una subtarea sube el evento entero`() = runTest {
        // Regresión: la subtarea solo se cambiaba en local y la siguiente sync la deshacía.
        // En Google las subtareas viven en la descripción del evento, por eso se sube el evento
        events.insertEvent(anEvent(id = "e1", subtasks = listOf(Subtask("A", false))))
        log.calls.clear()

        ToggleSubtaskCompletionUseCase(events, google)("e1", "A")

        assertEquals(listOf(Subtask("A", true)), events.events.value.getValue("e1").subtasks)
        assertEquals(listOf("toggleSubtask(e1, A)", "pushEvent(e1)"), log.calls)
    }
}
