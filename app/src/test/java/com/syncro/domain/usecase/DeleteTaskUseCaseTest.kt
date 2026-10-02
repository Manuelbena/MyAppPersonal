package com.syncro.domain.usecase

import com.syncro.testutil.CallLog
import com.syncro.testutil.FakeGoogleSyncRepository
import com.syncro.testutil.FakeTaskRepository
import com.syncro.testutil.aTask
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [DeleteTaskUseCase]: primero se borra en el móvil (la app responde al momento, también sin
 * conexión) y después se sube el borrado a Google.
 */
class DeleteTaskUseCaseTest {

    private val log = CallLog()
    private val tasks = FakeTaskRepository(log)
    private val google = FakeGoogleSyncRepository(log)

    @Test
    fun `borra en local y despues lo sube a Google`() = runTest {
        tasks.insertTask(aTask(id = "t1"))
        log.calls.clear()

        DeleteTaskUseCase(tasks, google)("t1")

        assertEquals(listOf("deleteTask(t1)", "pushTask(t1)"), log.calls)
        assertNull(tasks.getTaskById("t1"))
    }

    @Test
    fun `sin conexion la tarea desaparece igualmente de la app`() = runTest {
        tasks.insertTask(aTask(id = "t1"))
        google.isOffline = true

        DeleteTaskUseCase(tasks, google)("t1")

        assertNull(tasks.getTaskById("t1"))
    }
}
