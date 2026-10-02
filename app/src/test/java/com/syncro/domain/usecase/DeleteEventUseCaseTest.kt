package com.syncro.domain.usecase

import com.syncro.testutil.CallLog
import com.syncro.testutil.FakeEventRepository
import com.syncro.testutil.FakeGoogleSyncRepository
import com.syncro.testutil.anEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [DeleteEventUseCase]: primero se borra en el móvil (la app responde al momento, también sin
 * conexión) y después se sube el borrado a Google.
 */
class DeleteEventUseCaseTest {

    private val log = CallLog()
    private val events = FakeEventRepository(log)
    private val google = FakeGoogleSyncRepository(log)

    @Test
    fun `borra en local y despues lo sube a Google`() = runTest {
        events.insertEvent(anEvent(id = "e1"))
        log.calls.clear()

        DeleteEventUseCase(events, google)("e1")

        assertEquals(listOf("deleteEvent(e1)", "pushEvent(e1)"), log.calls)
        assertNull(events.getEventById("e1"))
    }

    @Test
    fun `sin conexion el evento desaparece igualmente de la app`() = runTest {
        events.insertEvent(anEvent(id = "e1"))
        google.isOffline = true

        DeleteEventUseCase(events, google)("e1")

        assertNull(events.getEventById("e1"))
    }
}
