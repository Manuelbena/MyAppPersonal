package com.syncro.data.repository

import com.syncro.data.local.SyncroDatabase
import com.syncro.data.local.entity.RepeatSeriesEntity
import com.syncro.domain.model.Priority
import com.syncro.domain.model.Recurrence
import com.syncro.domain.model.RepeatFrequency
import com.syncro.domain.model.RepeatSeries
import com.syncro.domain.model.Subtask
import com.syncro.testutil.DAY
import com.syncro.testutil.aTask
import com.syncro.testutil.anEvent
import com.syncro.testutil.at
import com.syncro.testutil.createInMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY

/**
 * Plan de pruebas de [RepeatSeriesRepositoryImpl] y de cómo las tareas y eventos muestran su serie.
 *
 * Responsabilidades: guardar y leer la regla y la plantilla tal cual (días de la semana, hora,
 * duración, subtareas), y que cada repetición lleve cómo se repite.
 *
 * Riesgos: perder los días de la semana o la duración al guardar (repeticiones en días u horas
 * equivocados), y una fila que no se entiende rompiendo la lectura de todas.
 */
@RunWith(RobolectricTestRunner::class)
class RepeatSeriesRepositoryImplTest {

    private lateinit var db: SyncroDatabase
    private lateinit var repository: RepeatSeriesRepositoryImpl

    @Before
    fun setUp() {
        db = createInMemoryDatabase()
        repository = RepeatSeriesRepositoryImpl(db.repeatSeriesDao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val weekly = Recurrence(RepeatFrequency.WEEKLY, setOf(MONDAY, THURSDAY))

    @Test
    fun `una serie de tareas se lee igual que se guardo`() = runTest {
        val series = RepeatSeries(
            id = "s1",
            recurrence = weekly,
            start = DAY,
            until = DAY.plusMonths(2),
            generatedUntil = DAY.plusDays(10),
            template = aTask(id = "s1", title = "Clase de inglés", date = DAY, time = at("00:00")).copy(seriesId = "s1", repeat = weekly)
        )

        repository.saveSeries(series)

        assertEquals(series, repository.getSeries("s1"))
    }

    @Test
    fun `una serie de eventos conserva horas, duracion de varios dias y subtareas sin marcar`() = runTest {
        val template = anEvent(
            id = "s2",
            title = "Turno de noche",
            date = DAY,
            endDate = DAY.plusDays(1),
            startTime = at("22:00"),
            endTime = at("06:00"),
            priority = Priority.HIGH,
            subtasks = listOf(Subtask("Llevar cena", isCompleted = false)),
            location = "Hospital"
        ).copy(seriesId = "s2", repeat = weekly, reminderMinutes = 30)
        val series = RepeatSeries("s2", weekly, DAY, null, DAY, template)

        repository.saveSeries(series)

        assertEquals(series, repository.getSeries("s2"))
    }

    @Test
    fun `una fila con una regla desconocida se ignora sin romper las demas`() = runTest {
        repository.saveSeries(RepeatSeries("buena", Recurrence(RepeatFrequency.DAILY), DAY, null, DAY, aTask(id = "buena", date = DAY)))
        db.repeatSeriesDao.upsert(
            RepeatSeriesEntity(
                id = "rara", kind = "TASK", frequency = "HOURLY", weekdays = "", startDate = DAY.toEpochDay(),
                untilDate = null, generatedUntil = DAY.toEpochDay(), title = "?", description = null, location = null,
                startTime = "00:00", endTime = "00:00", spanDays = 0, categoryText = null, categoryColor = null,
                priority = null, subtasks = "", reminderMinutes = null
            )
        )

        assertEquals(listOf("buena"), repository.getAllSeries().map { it.id })
    }

    @Test
    fun `cada repeticion lleva como se repite y una suelta no`() = runTest {
        val tasks = TaskRepositoryImpl(db.taskDao, db.repeatSeriesDao)
        repository.saveSeries(RepeatSeries("s1", weekly, DAY, null, DAY, aTask(id = "s1", date = DAY)))
        tasks.insertTask(aTask(id = "repetida", date = DAY).copy(seriesId = "s1"))
        tasks.insertTask(aTask(id = "suelta", date = DAY))

        val byId = tasks.getTasksByDate(DAY).first().associateBy { it.id }

        assertEquals(weekly, byId.getValue("repetida").repeat)
        assertNull(byId.getValue("suelta").repeat)
        assertEquals(weekly, tasks.getTaskById("repetida")!!.repeat)
    }

    @Test
    fun `las repeticiones de una serie desde un dia no incluyen las anteriores ni las borradas`() = runTest {
        val events = EventRepositoryImpl(db.eventDao, db.repeatSeriesDao)
        events.insertEvent(anEvent(id = "antes", date = DAY).copy(seriesId = "s"))
        events.insertEvent(anEvent(id = "desde", date = DAY.plusDays(7)).copy(seriesId = "s"))
        events.insertEvent(anEvent(id = "borrada", date = DAY.plusDays(14)).copy(seriesId = "s"))
        events.insertEvent(anEvent(id = "otra-serie", date = DAY.plusDays(7)).copy(seriesId = "x"))
        events.deleteEvent("borrada")

        assertEquals(listOf("desde"), events.getEventIdsInSeries("s", DAY.plusDays(1)))
    }
}
