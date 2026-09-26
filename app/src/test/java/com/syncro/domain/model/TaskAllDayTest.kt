package com.syncro.domain.model

import com.syncro.testutil.aTask
import com.syncro.testutil.at
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Valores límite de [SyncroItem.Task.isAllDay]: solo las 00:00 exactas son "todo el día". */
class TaskAllDayTest {

    @Test
    fun `una tarea a las 00-00 es de todo el dia`() {
        assertTrue(aTask(time = at("00:00")).isAllDay)
    }

    @Test
    fun `una tarea a las 00-01 tiene hora`() {
        assertFalse(aTask(time = at("00:01")).isAllDay)
    }

    @Test
    fun `una tarea a las 23-59 tiene hora`() {
        assertFalse(aTask(time = at("23:59")).isAllDay)
    }
}
