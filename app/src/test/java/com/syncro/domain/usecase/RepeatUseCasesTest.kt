package com.syncro.domain.usecase

import com.syncro.domain.model.ArgbColor
import com.syncro.domain.model.REPEAT_HORIZON_DAYS
import com.syncro.domain.model.REPEAT_MAX_AHEAD_DAYS
import com.syncro.domain.model.Recurrence
import com.syncro.domain.model.RepeatFrequency
import com.syncro.domain.model.RepeatScope
import com.syncro.domain.model.SyncroItem
import com.syncro.testutil.CallLog
import com.syncro.testutil.DAY
import com.syncro.testutil.FakeEventRepository
import com.syncro.testutil.FakeGoogleSyncRepository
import com.syncro.testutil.FakeRepeatSeriesRepository
import com.syncro.testutil.FakeSettingsRepository
import com.syncro.testutil.FakeTaskRepository
import com.syncro.testutil.MutableClock
import com.syncro.testutil.aTask
import com.syncro.testutil.at
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Plan: tareas y eventos que se repiten. Responsabilidades: crear la serie y sus repeticiones
 * (como tareas/eventos normales) hasta el horizonte, ir creando más con los días o al mirar meses
 * futuros, borrar o cambiar "solo esta" o "esta y las siguientes", y subir todo a Google.
 * Riesgos: repeticiones duplicadas (en el móvil y en Google), que una borrada vuelva a aparecer,
 * que cambiar "esta y las siguientes" toque las anteriores, y que una tarea que se repite se
 * proponga pasar a otro día en el repaso de pendientes.
 */
class RepeatUseCasesTest {

    // DAY (26 de septiembre de 2026) es sábado
    private val clock = MutableClock(DAY.atTime(9, 0).toInstant(ZoneOffset.UTC))
    private val log = CallLog()
    private val tasks = FakeTaskRepository(log)
    private val events = FakeEventRepository(log)
    private val google = FakeGoogleSyncRepository(log)
    private val series = FakeRepeatSeriesRepository()
    private val generate = GenerateRepeatsUseCase(series, tasks, events, google, clock)
    private val saveTask = SaveTaskUseCase(tasks, google, series, generate)
    private val saveEvent = SaveEventUseCase(events, google, series, generate)
    private val deleteTask = DeleteTaskUseCase(tasks, google, series)
    private val deleteEvent = DeleteEventUseCase(events, google, series)

    private val daily = Recurrence(RepeatFrequency.DAILY)

    private fun taskDates() = tasks.tasks.value.values.map { it.date }.sorted()
    private fun taskOn(date: LocalDate) = tasks.tasks.value.values.single { it.date == date }
    private fun eventOn(date: LocalDate) = events.events.value.values.single { it.date == date }

    private fun moveClockTo(date: LocalDate) {
        clock.instant = date.atTime(9, 0).toInstant(ZoneOffset.UTC)
    }

    private suspend fun createWeeklyEvent(title: String = "Gimnasio") = saveEvent(
        title = title,
        description = null,
        location = null,
        date = DAY,
        startTime = at("19:00"),
        endTime = at("20:00"),
        categoryText = "Deporte",
        categoryColor = ArgbColor(0xFFEC4899),
        priority = null,
        subtasks = emptyList(),
        repeat = Recurrence(RepeatFrequency.WEEKLY, setOf(DAY.dayOfWeek))
    )

    private suspend fun editEvent(event: SyncroItem.Event, title: String, repeat: Recurrence?, scope: RepeatScope) = saveEvent(
        id = event.id,
        title = title,
        description = event.description,
        location = event.location,
        date = event.date,
        startTime = event.startTime,
        endTime = event.endTime,
        categoryText = event.categoryText,
        categoryColor = event.categoryColor,
        priority = event.priority,
        subtasks = emptyList(),
        repeat = repeat,
        scope = scope
    )

    // region Crear

    @Test
    fun `una tarea diaria crea las de los proximos dias como tareas de su serie y las sube de una vez`() = runTest {
        saveTask(title = "Tomar la pastilla", description = "", date = DAY, time = at("00:00"), repeat = daily)

        val all = tasks.tasks.value.values
        assertEquals(REPEAT_HORIZON_DAYS + 1, all.size.toLong())
        assertEquals(DAY, taskDates().first())
        assertEquals(DAY.plusDays(REPEAT_HORIZON_DAYS), taskDates().last())
        assertEquals(1, all.map { it.seriesId }.distinct().size)
        assertTrue(all.all { it.title == "Tomar la pastilla" && it.repeat == daily })
        // Una sola subida para todas, después de guardarlas
        assertEquals(1, log.calls.count { it == "pushPendingChanges" })
        assertEquals("pushPendingChanges", log.calls.last())
        assertTrue(google.pushedTaskIds.isEmpty())
    }

    @Test
    fun `cada semana solo cae en los dias elegidos, empezando por el primero que toca`() = runTest {
        saveTask(title = "Clase", description = "", date = DAY, time = at("00:00"), repeat = Recurrence(RepeatFrequency.WEEKLY, setOf(MONDAY, WEDNESDAY, FRIDAY)))

        // DAY es sábado: la primera es el lunes 28
        assertEquals(LocalDate.of(2026, 9, 28), taskDates().first())
        assertTrue(taskDates().all { it.dayOfWeek in setOf(MONDAY, WEDNESDAY, FRIDAY) })
    }

    @Test
    fun `un titulo vacio no crea la serie`() = runTest {
        val result = saveTask(title = " ", description = "", date = DAY, time = at("00:00"), repeat = daily)

        assertTrue(result.isFailure)
        assertTrue(series.series.value.isEmpty())
    }

    // endregion

    // region Generar más

    @Test
    fun `generar otra vez no duplica y al pasar un dia crea solo la nueva`() = runTest {
        saveTask(title = "Regar", description = "", date = DAY, time = at("00:00"), repeat = daily)
        val before = tasks.tasks.value.size

        assertEquals(0, generate())
        moveClockTo(DAY.plusDays(1))
        assertEquals(1, generate())

        assertEquals(before + 1, tasks.tasks.value.size)
        assertEquals(taskDates().distinct(), taskDates())
    }

    @Test
    fun `mirar un mes lejano en el Calendario crea hasta ese mes, con tope de un ano`() = runTest {
        saveTask(title = "Regar", description = "", date = DAY, time = at("00:00"), repeat = Recurrence(RepeatFrequency.MONTHLY))

        generate(until = DAY.plusMonths(6))
        assertEquals(DAY.plusMonths(6), taskDates().last())

        generate(until = DAY.plusYears(5))
        assertTrue(!taskDates().last().isAfter(DAY.plusDays(REPEAT_MAX_AHEAD_DAYS)))
    }

    // endregion

    // region Borrar

    @Test
    fun `borrar solo una repeticion no la vuelve a crear`() = runTest {
        saveTask(title = "Regar", description = "", date = DAY, time = at("00:00"), repeat = daily)
        val monday = taskOn(DAY.plusDays(2))

        deleteTask(monday.id, scope = RepeatScope.THIS)
        moveClockTo(DAY.plusDays(1))
        generate()

        assertTrue(DAY.plusDays(2) !in taskDates())
        assertTrue(DAY.plusDays(3) in taskDates())
    }

    @Test
    fun `borrar esta y las siguientes deja las anteriores y no crea mas`() = runTest {
        saveTask(title = "Regar", description = "", date = DAY, time = at("00:00"), repeat = daily)
        val seriesId = taskOn(DAY).seriesId!!
        val cut = DAY.plusDays(5)

        deleteTask(taskOn(cut).id, scope = RepeatScope.THIS_AND_FOLLOWING)
        moveClockTo(DAY.plusDays(30))
        generate()

        assertEquals((0L until 5L).map { DAY.plusDays(it) }, taskDates())
        assertEquals(cut.minusDays(1), series.series.value.getValue(seriesId).until)
        assertEquals("pushPendingChanges", log.calls.last())
    }

    @Test
    fun `borrar desde la primera repeticion borra la serie entera`() = runTest {
        saveTask(title = "Regar", description = "", date = DAY, time = at("00:00"), repeat = daily)

        deleteTask(taskOn(DAY).id, scope = RepeatScope.THIS_AND_FOLLOWING)

        assertTrue(tasks.tasks.value.isEmpty())
        assertTrue(series.series.value.isEmpty())
    }

    @Test
    fun `en una tarea suelta, esta y las siguientes es lo mismo que borrarla`() = runTest {
        tasks.insertTask(aTask(id = "suelta"))
        tasks.insertTask(aTask(id = "otra", date = DAY.plusDays(1)))

        deleteTask("suelta", scope = RepeatScope.THIS_AND_FOLLOWING)

        assertEquals(listOf("otra"), tasks.tasks.value.keys.toList())
    }

    // endregion

    // region Editar eventos

    @Test
    fun `cambiar solo este evento deja igual el resto de la serie`() = runTest {
        createWeeklyEvent()
        val second = eventOn(DAY.plusWeeks(1))

        editEvent(second, "Gimnasio con Ana", second.repeat, RepeatScope.THIS)

        assertEquals("Gimnasio con Ana", eventOn(DAY.plusWeeks(1)).title)
        assertEquals("Gimnasio", eventOn(DAY.plusWeeks(2)).title)
        assertEquals(second.seriesId, eventOn(DAY.plusWeeks(1)).seriesId)
    }

    @Test
    fun `cambiar este y los siguientes deja los anteriores y empieza una serie nueva desde este`() = runTest {
        createWeeklyEvent()
        val first = eventOn(DAY)
        val third = eventOn(DAY.plusWeeks(2))

        editEvent(third, "Pilates", third.repeat, RepeatScope.THIS_AND_FOLLOWING)

        assertEquals("Gimnasio", eventOn(DAY.plusWeeks(1)).title)
        assertEquals("Pilates", eventOn(DAY.plusWeeks(2)).title)
        assertEquals("Pilates", eventOn(DAY.plusWeeks(3)).title)
        // El editado conserva su id (no se duplica en Google) y pasa a la serie nueva
        assertEquals(third.id, eventOn(DAY.plusWeeks(2)).id)
        assertNotEquals(first.seriesId, eventOn(DAY.plusWeeks(2)).seriesId)
        assertEquals(eventOn(DAY.plusWeeks(2)).seriesId, eventOn(DAY.plusWeeks(3)).seriesId)
        // La serie vieja acaba el día antes y ya no crea más
        assertEquals(DAY.plusWeeks(2).minusDays(1), series.series.value.getValue(first.seriesId!!).until)
        val dates = events.events.value.values.map { it.date }
        assertEquals(dates.distinct().size, dates.size)
    }

    @Test
    fun `dejar de repetir desde un evento borra los siguientes y este queda suelto`() = runTest {
        createWeeklyEvent()
        val second = eventOn(DAY.plusWeeks(1))

        editEvent(second, second.title, repeat = null, scope = RepeatScope.THIS_AND_FOLLOWING)

        assertEquals(listOf(DAY, DAY.plusWeeks(1)), events.events.value.values.map { it.date }.sorted())
        assertNull(eventOn(DAY.plusWeeks(1)).seriesId)
    }

    @Test
    fun `un evento suelto que pasa a repetirse es la primera repeticion`() = runTest {
        saveEvent(
            title = "Reunión", description = null, location = null, date = DAY,
            startTime = at("10:00"), endTime = at("11:00"), categoryText = "Trabajo",
            categoryColor = ArgbColor(0xFF6366F1), priority = null, subtasks = emptyList()
        )
        val single = eventOn(DAY)

        editEvent(single, single.title, Recurrence.weeklyOn(DAY), RepeatScope.THIS)

        assertEquals(single.id, eventOn(DAY).id)
        assertEquals(eventOn(DAY).seriesId, eventOn(DAY.plusWeeks(1)).seriesId)
    }

    @Test
    fun `borrar este evento y los siguientes`() = runTest {
        createWeeklyEvent()

        deleteEvent(eventOn(DAY.plusWeeks(1)).id, scope = RepeatScope.THIS_AND_FOLLOWING)

        assertEquals(listOf(DAY), events.events.value.values.map { it.date })
    }

    // endregion

    @Test
    fun `una tarea que se repite no se propone pasar a hoy en el repaso`() = runTest {
        tasks.insertTask(aTask(id = "suelta", date = DAY.minusDays(1)))
        tasks.insertTask(aTask(id = "repetida", date = DAY.minusDays(1)).copy(seriesId = "s"))

        val leftovers = GetLeftoverTasksUseCase(tasks, FakeSettingsRepository(), clock)().first()

        assertEquals(listOf("suelta"), leftovers.tasks.map { it.id })
    }
}
