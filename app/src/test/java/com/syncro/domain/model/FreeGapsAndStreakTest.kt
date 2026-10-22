package com.syncro.domain.model

import com.syncro.testutil.DAY
import com.syncro.testutil.aTask
import com.syncro.testutil.anEvent
import com.syncro.testutil.at
import org.junit.Assert.assertEquals
import org.junit.Test

class FreeGapsAndStreakTest {

    // region Huecos libres

    @Test
    fun `hay hueco entre dos eventos separados al menos media hora`() {
        val events = listOf(
            anEvent(startTime = at("09:00"), endTime = at("10:00")),
            anEvent(startTime = at("10:15"), endTime = at("11:00")), // 15 min: no es hueco
            anEvent(startTime = at("12:30"), endTime = at("13:00"))
        )

        assertEquals(mapOf(2 to FreeGap(at("11:00"), at("12:30"))), freeGapsBefore(events, DAY))
    }

    @Test
    fun `un evento largo que tapa a otro no deja hueco falso`() {
        val events = listOf(
            anEvent(startTime = at("09:00"), endTime = at("14:00")),
            anEvent(startTime = at("10:00"), endTime = at("11:00")),
            anEvent(startTime = at("14:30"), endTime = at("15:00"))
        )

        assertEquals(mapOf(2 to FreeGap(at("14:00"), at("14:30"))), freeGapsBefore(events, DAY))
    }

    @Test
    fun `los de todo el dia no cuentan y hoy el hueco empieza ahora`() {
        val events = listOf(
            anEvent(startTime = at("00:00"), endTime = at("00:00")),
            anEvent(startTime = at("08:00"), endTime = at("09:00")),
            anEvent(startTime = at("13:00"), endTime = at("14:00"))
        )

        assertEquals(mapOf(2 to FreeGap(at("11:20"), at("13:00"))), freeGapsBefore(events, DAY, now = DAY.atTime(11, 20)))
        // A las 12:45 quedan 15 min: ya no es hueco
        assertEquals(emptyMap<Int, FreeGap>(), freeGapsBefore(events, DAY, now = DAY.atTime(12, 45)))
    }

    // endregion

    // region Racha

    @Test
    fun `los dias sin tareas no rompen la racha y hoy sin terminar tampoco`() {
        val tasks = listOf(
            aTask(date = DAY.minusDays(4), isCompleted = false), // aquí se corta
            aTask(date = DAY.minusDays(3), isCompleted = true),
            // DAY - 2: sin tareas
            aTask(date = DAY.minusDays(1), isCompleted = true),
            aTask(date = DAY.minusDays(1), isCompleted = true),
            aTask(date = DAY, isCompleted = false)
        )

        assertEquals(2, taskStreak(tasks, DAY))
    }

    @Test
    fun `hoy suma cuando ya esta todo hecho`() {
        val tasks = listOf(aTask(date = DAY.minusDays(1), isCompleted = true), aTask(date = DAY, isCompleted = true))

        assertEquals(2, taskStreak(tasks, DAY))
    }

    // endregion
}
