package com.syncro.domain.model

import com.syncro.testutil.anEvent
import com.syncro.testutil.at
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Valores límite de [SyncroItem.Event.isAllDay]: solo 00:00–00:00 exacto es día completo. */
class EventAllDayTest {

    @Test
    fun `00-00 a 00-00 es dia completo`() {
        assertTrue(anEvent(startTime = at("00:00"), endTime = at("00:00")).isAllDay)
    }

    @Test
    fun `00-00 a 00-01 no es dia completo`() {
        assertFalse(anEvent(startTime = at("00:00"), endTime = at("00:01")).isAllDay)
    }

    @Test
    fun `mismo inicio y fin fuera de medianoche no es dia completo`() {
        assertFalse(anEvent(startTime = at("10:00"), endTime = at("10:00")).isAllDay)
    }
}
