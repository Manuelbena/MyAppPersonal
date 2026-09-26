package com.syncro.data.repository

import com.syncro.domain.model.ArgbColor
import com.syncro.data.local.SyncroDatabase
import com.syncro.data.local.dao.EventDao
import com.syncro.data.local.entity.SubtaskEntity
import com.syncro.domain.model.Priority
import com.syncro.domain.model.Subtask
import com.syncro.testutil.DAY
import com.syncro.testutil.at
import com.syncro.testutil.aSyncedEventEntity
import com.syncro.testutil.anEvent
import com.syncro.testutil.createInMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.syncro.domain.model.SyncroItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows

/**
 * Plan de pruebas de [EventRepositoryImpl]
 *
 * Responsabilidades: crear/editar eventos con sus subtareas, consultarlos por día/rango,
 * completar eventos y subtareas dejando el cambio pendiente de subir a Google.
 *
 * Riesgos principales (varios ya ocurrieron y tienen test de regresión):
 *  - Editar un evento perdía su remoteId y se duplicaba en Google.
 *  - Editar dejaba subtareas antiguas o, con lista vacía, no borraba las anteriores.
 *  - Completar una subtarea no marcaba el evento como pendiente y la sync lo revertía.
 */
@RunWith(RobolectricTestRunner::class)
class EventRepositoryImplTest {

    private lateinit var db: SyncroDatabase
    private lateinit var dao: EventDao
    private lateinit var repository: EventRepositoryImpl

    @Before
    fun setUp() {
        db = createInMemoryDatabase()
        dao = db.eventDao
        repository = EventRepositoryImpl(dao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // region Creación

    @Test
    fun `evento nuevo se guarda con su id y queda pendiente de subir`() = runTest {
        val id = save(anEvent(id = "e1"))

        assertEquals("e1", id)
        val entity = dao.getEventById(id)!!
        assertNull(entity.remoteId)
        assertEquals(1, entity.pendingChanges)
    }

    @Test
    fun `los datos del evento se conservan al guardar y leer`() = runTest {
        val event = anEvent(
            title = "Reunión",
            description = "Sprint",
            startTime = at("09:30"),
            endTime = at("10:15"),
            categoryText = "Trabajo",
            categoryColor = ArgbColor(0xFF6366F1),
            priority = Priority.HIGH,
            subtasks = listOf(Subtask("Preparar slides", true), Subtask("Enviar acta", false)),
            location = "Oficina"
        )

        val saved = repository.getEventById(save(event))!!

        assertEquals("Reunión", saved.title)
        assertEquals("Sprint", saved.description)
        assertEquals(DAY, saved.date)
        assertEquals(at("09:30"), saved.startTime)
        assertEquals(at("10:15"), saved.endTime)
        assertEquals(ArgbColor(0xFF6366F1), saved.categoryColor)
        assertEquals(Priority.HIGH, saved.priority)
        assertEquals(listOf(Subtask("Preparar slides", true), Subtask("Enviar acta", false)), saved.subtasks)
        assertEquals("Oficina", saved.location)
    }

    @Test
    fun `evento sin prioridad sigue sin prioridad al leerlo`() = runTest {
        val id = save(anEvent(priority = null))

        assertNull(repository.getEventById(id)!!.priority)
    }

    @Test
    fun `un evento sin id se rechaza`() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.insertEvent(anEvent(id = "")) }
        }
    }

    @Test
    fun `buscar un evento inexistente devuelve null`() = runTest {
        assertNull(repository.getEventById("no-existe"))
    }

    // endregion

    // region Edición (regresiones)

    @Test
    fun `editar un evento sincronizado conserva su remoteId`() = runTest {
        // Regresión: al editar se ponía remoteId = null y el evento se duplicaba en Google
        dao.insertEvent(aSyncedEventEntity(id = "e1", remoteId = "google-1"))

        save(anEvent(id = "e1", remoteId = null, title = "Título editado"))

        val entity = dao.getEventById("e1")!!
        assertEquals("google-1", entity.remoteId)
        assertEquals("Título editado", entity.title)
    }

    @Test
    fun `la ubicacion se conserva al leer el evento`() = runTest {
        // Regresión: el modelo de dominio no tenía ubicación y editar un evento la borraba
        val id = save(anEvent(location = "Gimnasio"))

        assertEquals("Gimnasio", repository.getEventById(id)!!.location)
    }

    @Test
    fun `cada edicion suma un cambio pendiente`() = runTest {
        dao.insertEvent(aSyncedEventEntity(id = "e1"))

        save(anEvent(id = "e1"))
        assertEquals(1, dao.getEventById("e1")!!.pendingChanges)

        save(anEvent(id = "e1"))
        assertEquals(2, dao.getEventById("e1")!!.pendingChanges)
    }

    @Test
    fun `editar sustituye la lista de subtareas completa`() = runTest {
        val id = save(anEvent(subtasks = listOf(Subtask("A", false), Subtask("B", true))))

        save(anEvent(id = id, subtasks = listOf(Subtask("C", false))))

        assertEquals(listOf(Subtask("C", false)), repository.getEventById(id)!!.subtasks)
    }

    @Test
    fun `editar con lista de subtareas vacia borra las anteriores`() = runTest {
        // Regresión: con lista vacía se hacía un insert simple y las subtareas viejas sobrevivían
        val id = save(anEvent(subtasks = listOf(Subtask("A", false))))

        save(anEvent(id = id, subtasks = emptyList()))

        assertTrue(repository.getEventById(id)!!.subtasks.isEmpty())
    }

    // endregion

    // region Completar evento y subtareas

    @Test
    fun `completar un evento lo marca y lo deja pendiente de subir`() = runTest {
        dao.insertEvent(aSyncedEventEntity(id = "e1"))

        repository.toggleEventCompletion("e1")

        val entity = dao.getEventById("e1")!!
        assertTrue(entity.isCompleted)
        assertEquals(1, entity.pendingChanges)
    }

    @Test
    fun `completar una subtarea solo cambia esa subtarea y deja el evento pendiente`() = runTest {
        // Regresión: el cambio de subtarea no se subía a Google y la siguiente sync lo deshacía
        dao.insertEventWithSubtasks(
            aSyncedEventEntity(id = "e1"),
            listOf(
                SubtaskEntity(eventId = "e1", title = "A", isCompleted = false),
                SubtaskEntity(eventId = "e1", title = "B", isCompleted = false)
            )
        )

        repository.toggleSubtaskCompletion("e1", "A")

        val event = repository.getEventById("e1")!!
        assertEquals(listOf(Subtask("A", true), Subtask("B", false)), event.subtasks)
        assertFalse(event.isCompleted)
        assertEquals(1, dao.getEventById("e1")!!.pendingChanges)
    }

    // endregion

    // region Consultas por fecha: valores límite

    @Test
    fun `eventos del dia excluye el dia anterior y el siguiente`() = runTest {
        listOf(DAY.minusDays(1), DAY, DAY.plusDays(1)).forEach { date ->
            save(anEvent(title = date.toString(), date = date))
        }

        val titles = repository.getEventsByDate(DAY).first().map { it.title }

        assertEquals(listOf(DAY.toString()), titles)
    }

    @Test
    fun `eventos en rango incluye los dos extremos`() = runTest {
        val start = DAY.withDayOfMonth(1)
        val end = DAY.withDayOfMonth(30)
        listOf(start.minusDays(1), start, end, end.plusDays(1)).forEach { date ->
            save(anEvent(title = date.toString(), date = date))
        }

        val titles = repository.getEventsInRange(start, end).first().map { it.title }.sorted()

        assertEquals(listOf(start.toString(), end.toString()), titles)
    }

    // endregion

    private suspend fun save(event: SyncroItem.Event): String {
        repository.insertEvent(event)
        return event.id
    }
}
