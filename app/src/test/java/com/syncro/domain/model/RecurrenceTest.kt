package com.syncro.domain.model

import com.syncro.testutil.anEvent
import com.syncro.testutil.aTask
import com.syncro.testutil.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

/**
 * Plan: las reglas de repetición. Responsabilidades: qué días cae cada repetición (día, semana con
 * sus días, mes, año), hasta dónde crear repeticiones y cómo es cada una. Riesgos: un mensual del
 * 31 o un anual del 29 de febrero que desaparece en meses cortos, repeticiones antes del inicio o
 * después del fin, y repeticiones que heredan el "completado" o el id de Google de la plantilla.
 */
class RecurrenceTest {

    private fun date(month: Int, day: Int, year: Int = 2026) = LocalDate.of(year, month, day)

    @Test
    fun `cada dia cae todos los dias desde el inicio`() {
        val daily = Recurrence(RepeatFrequency.DAILY)

        assertEquals(listOf(date(10, 1), date(10, 2), date(10, 3)), daily.occurrences(date(10, 1), date(9, 28), date(10, 3)))
    }

    @Test
    fun `cada semana cae solo en los dias elegidos`() {
        // El 5 de octubre de 2026 es lunes
        val weekly = Recurrence(RepeatFrequency.WEEKLY, setOf(MONDAY, WEDNESDAY, FRIDAY))

        assertEquals(
            listOf(date(10, 5), date(10, 7), date(10, 9), date(10, 12)),
            weekly.occurrences(date(10, 5), date(10, 5), date(10, 12))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cada semana sin ningun dia no es una regla valida`() {
        Recurrence(RepeatFrequency.WEEKLY, emptySet())
    }

    @Test
    fun `cada mes cae el mismo dia o el ultimo si el mes es mas corto`() {
        val monthly = Recurrence(RepeatFrequency.MONTHLY)

        assertEquals(
            listOf(date(1, 31), date(2, 28), date(3, 31), date(4, 30)),
            monthly.occurrences(date(1, 31), date(1, 1), date(4, 30))
        )
    }

    @Test
    fun `cada ano cae el mismo dia y el 29 de febrero pasa al 28`() {
        val yearly = Recurrence(RepeatFrequency.YEARLY)

        assertEquals(listOf(date(9, 27), date(9, 27, 2027)), yearly.occurrences(date(9, 27), date(1, 1), date(12, 31, 2027)))
        assertEquals(
            listOf(LocalDate.of(2028, 2, 29), LocalDate.of(2029, 2, 28)),
            yearly.occurrences(LocalDate.of(2028, 2, 29), LocalDate.of(2028, 1, 1), LocalDate.of(2029, 12, 31))
        )
    }

    @Test
    fun `nunca hay repeticiones antes del inicio`() {
        assertFalse(Recurrence(RepeatFrequency.DAILY).matches(date(9, 30), start = date(10, 1)))
    }

    @Test
    fun `la siguiente repeticion de un cumpleanos puede estar a casi un ano`() {
        val yearly = Recurrence(RepeatFrequency.YEARLY)

        assertEquals(date(9, 20, 2027), yearly.next(start = date(9, 20), from = date(10, 1)))
    }

    private fun series(
        recurrence: Recurrence,
        start: LocalDate,
        generatedUntil: LocalDate = start.minusDays(1),
        until: LocalDate? = null,
        template: SyncroItem = aTask(date = start)
    ) = RepeatSeries("s", recurrence, start, until, generatedUntil, template)

    @Test
    fun `se crean repeticiones hasta el horizonte y como minimo la siguiente`() {
        val today = date(10, 1)
        val horizon = today.plusDays(REPEAT_HORIZON_DAYS)

        assertEquals(horizon, series(Recurrence(RepeatFrequency.DAILY), today).generationTarget(today, horizon))
        // Un cumpleaños a once meses: se crea aunque quede lejos del horizonte
        assertEquals(date(9, 20, 2027), series(Recurrence(RepeatFrequency.YEARLY), date(9, 20)).generationTarget(today, horizon))
    }

    @Test
    fun `con fecha de fin no se crea nada despues`() {
        val today = date(10, 1)
        val ended = series(Recurrence(RepeatFrequency.DAILY), date(9, 1), until = date(10, 10))

        assertEquals(date(10, 10), ended.generationTarget(today, today.plusDays(REPEAT_HORIZON_DAYS)))
    }

    @Test
    fun `solo se crean las que faltan desde lo ya creado`() {
        val daily = series(Recurrence(RepeatFrequency.DAILY), date(10, 1), generatedUntil = date(10, 3))

        assertEquals(listOf(date(10, 4), date(10, 5)), daily.datesToGenerate(date(10, 5)))
        assertTrue(daily.datesToGenerate(date(10, 3)).isEmpty())
    }

    @Test
    fun `cada repeticion es nueva, sin completar ni id de Google, y de la serie`() {
        val template = aTask(id = "plantilla", date = date(10, 1), isCompleted = true).copy(remoteId = "google-1")
        val task = series(Recurrence(RepeatFrequency.DAILY), date(10, 1), template = template).occurrence(date(10, 4), "nueva") as SyncroItem.Task

        assertEquals("nueva", task.id)
        assertEquals(date(10, 4), task.date)
        assertFalse(task.isCompleted)
        assertEquals(null, task.remoteId)
        assertEquals("s", task.seriesId)
    }

    @Test
    fun `un evento que cruza la medianoche dura lo mismo en cada repeticion y sin subtareas hechas`() {
        val template = anEvent(
            date = date(10, 1),
            endDate = date(10, 2),
            startTime = at("22:00"),
            endTime = at("02:00"),
            subtasks = listOf(Subtask("Llevar música", isCompleted = true))
        )
        val event = series(Recurrence(RepeatFrequency.WEEKLY, setOf(template.date.dayOfWeek)), date(10, 1), template = template)
            .occurrence(date(10, 8), "e") as SyncroItem.Event

        assertEquals(date(10, 8), event.date)
        assertEquals(date(10, 9), event.endDate)
        assertFalse(event.subtasks.single().isCompleted)
    }
}
