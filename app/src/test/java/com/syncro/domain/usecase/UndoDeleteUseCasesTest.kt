package com.syncro.domain.usecase

import com.syncro.testutil.CallLog
import com.syncro.testutil.FakeEventRepository
import com.syncro.testutil.FakeGoogleSyncRepository
import com.syncro.testutil.FakeNoteRepository
import com.syncro.testutil.FakeTaskRepository
import com.syncro.testutil.aNote
import com.syncro.testutil.aTask
import com.syncro.testutil.anEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan de pruebas del "Deshacer" tras borrar: el borrado diferido (sin subir) y la recuperación.
 *
 * Riesgos: subir el borrado a Google antes de que el usuario pueda deshacerlo, recuperar una copia
 * distinta (otro id, sin subtareas) y prometer que se recuperó algo que ya se borró en Google.
 */
class UndoDeleteUseCasesTest {

    private val log = CallLog()
    private val tasks = FakeTaskRepository(log)
    private val events = FakeEventRepository(log)
    private val google = FakeGoogleSyncRepository(log)

    @Test
    fun `con uploadNow a false el borrado no se sube`() = runTest {
        tasks.insertTask(aTask(id = "t1"))
        events.insertEvent(anEvent(id = "e1"))
        log.calls.clear()

        DeleteTaskUseCase(tasks, google)("t1", uploadNow = false)
        DeleteEventUseCase(events, google)("e1", uploadNow = false)

        assertEquals(listOf("deleteTask(t1)", "deleteEvent(e1)"), log.calls)
    }

    @Test
    fun `deshacer recupera la misma tarea y despues la sube`() = runTest {
        val task = aTask(id = "t1", title = "Llamar al banco")
        tasks.insertTask(task)
        DeleteTaskUseCase(tasks, google)("t1", uploadNow = false)
        log.calls.clear()

        assertTrue(UndoDeleteTaskUseCase(tasks, google)("t1"))

        assertEquals(task, tasks.getTaskById("t1"))
        assertEquals(listOf("restoreTask(t1)", "pushTask(t1)"), log.calls)
    }

    @Test
    fun `deshacer un evento ya borrado del todo no sube nada y lo dice`() = runTest {
        assertFalse(UndoDeleteEventUseCase(events, google)("e-ya-subido"))
        assertTrue(google.pushedEventIds.isEmpty())
    }

    @Test
    fun `deshacer una nota la guarda igual que estaba`() = runTest {
        val notes = FakeNoteRepository()
        val note = aNote(id = "n1")

        UndoDeleteNoteUseCase(notes)(note)

        assertEquals(note, notes.getNoteById("n1"))
    }
}
