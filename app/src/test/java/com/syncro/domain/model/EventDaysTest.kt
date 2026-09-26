package com.syncro.domain.model

import com.syncro.testutil.DAY
import com.syncro.testutil.anEvent
import com.syncro.testutil.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * En qué días se muestra un evento ([SyncroItem.Event.occursOn] y [SyncroItem.Event.days]).
 * Valores límite: el día antes y el día después, y el caso de terminar justo a las 00:00.
 */
class EventDaysTest {

    @Test
    fun `un evento normal solo ocupa su dia`() {
        val event = anEvent(date = DAY, startTime = at("10:00"), endTime = at("11:00"))

        assertEquals(listOf(DAY), event.days)
        assertFalse(event.occursOn(DAY.minusDays(1)))
        assertFalse(event.occursOn(DAY.plusDays(1)))
    }

    @Test
    fun `un evento que cruza la medianoche aparece en los dos dias`() {
        val event = anEvent(date = DAY, endDate = DAY.plusDays(1), startTime = at("21:30"), endTime = at("01:00"))

        assertEquals(listOf(DAY, DAY.plusDays(1)), event.days)
    }

    @Test
    fun `un evento que termina justo a las 00-00 no aparece al dia siguiente`() {
        // 22:00 → 00:00 del día siguiente: no ocupa ni un minuto de ese día
        val event = anEvent(date = DAY, endDate = DAY.plusDays(1), startTime = at("22:00"), endTime = at("00:00"))

        assertEquals(listOf(DAY), event.days)
    }

    @Test
    fun `un evento de varios dias aparece en todos los intermedios`() {
        val event = anEvent(date = DAY, endDate = DAY.plusDays(3), startTime = at("18:00"), endTime = at("12:00"))

        assertEquals(4, event.days.size)
        assertTrue(event.occursOn(DAY.plusDays(2)))
    }

    @Test
    fun `un evento de dia completo de varios dias ocupa cada dia incluido el ultimo`() {
        val event = anEvent(date = DAY, endDate = DAY.plusDays(2), startTime = at("00:00"), endTime = at("00:00"))

        assertTrue(event.isAllDay)
        assertEquals(listOf(DAY, DAY.plusDays(1), DAY.plusDays(2)), event.days)
    }
}
