package com.syncro.domain.usecase

import com.syncro.domain.model.ArgbColor
import com.syncro.domain.model.BlankTitleException
import com.syncro.domain.model.InvalidEventTimeRangeException
import com.syncro.domain.model.Priority
import com.syncro.domain.model.Subtask
import com.syncro.testutil.CallLog
import com.syncro.testutil.DAY
import com.syncro.testutil.at
import com.syncro.testutil.FakeEventRepository
import com.syncro.testutil.FakeGoogleSyncRepository
import com.syncro.testutil.anEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Plan de pruebas de [SaveEventUseCase]
 *
 * Reglas de negocio: título obligatorio, fin no anterior al inicio, id asignado por el dominio,
 * al editar se conserva lo que el formulario no gestiona (remoteId, completado, subtareas hechas).
 *
 * Riesgos: guardar datos inválidos que Google rechazará siempre, duplicar eventos en Google al
 * editar, perder el progreso del usuario al editar y que un fallo de red impida guardar.
 */
class SaveEventUseCaseTest {

    private lateinit var log: CallLog
    private lateinit var events: FakeEventRepository
    private lateinit var google: FakeGoogleSyncRepository
    private lateinit var saveEvent: SaveEventUseCase

    @Before
    fun setUp() {
        log = CallLog()
        events = FakeEventRepository(log)
        google = FakeGoogleSyncRepository(log)
        saveEvent = SaveEventUseCase(events, google)
    }

    // region Crear

    @Test
    fun `crear un evento lo guarda con un id nuevo y lo sube a Google`() = runTest {
        val result = save(title = "Reunión")

        assertTrue(result.isSuccess)
        val saved = events.events.value.values.single()
        assertEquals("Reunión", saved.title)
        // Primero en local (fuente de verdad) y después a Google, con el mismo id
        assertEquals(listOf("insertEvent(${saved.id})", "pushEvent(${saved.id})"), log.calls)
    }

    @Test
    fun `dos eventos identicos creados seguidos tienen ids distintos`() = runTest {
        save(title = "Café")
        save(title = "Café")

        val ids = events.events.value.keys.toList()
        assertEquals(2, ids.size)
        assertNotEquals(ids[0], ids[1])
    }

    @Test
    fun `se guardan la ubicacion y el resto de datos del formulario`() = runTest {
        save(title = "Médico", location = "Centro de salud", priority = Priority.HIGH, date = DAY.plusDays(3))

        val saved = events.events.value.values.single()
        assertEquals("Centro de salud", saved.location)
        assertEquals(Priority.HIGH, saved.priority)
        assertEquals(DAY.plusDays(3), saved.date)
    }

    @Test
    fun `los espacios alrededor del titulo se eliminan`() = runTest {
        save(title = "  Reunión  ")

        assertEquals("Reunión", events.events.value.values.single().title)
    }

    // endregion

    // region Validación: si falla, no se guarda ni se sube nada

    @Test
    fun `titulo vacio se rechaza`() = runTest {
        assertRejected(save(title = ""), BlankTitleException::class.java)
    }

    @Test
    fun `titulo con solo espacios se rechaza`() = runTest {
        assertRejected(save(title = "   "), BlankTitleException::class.java)
    }

    @Test
    fun `fin anterior al inicio se rechaza`() = runTest {
        assertRejected(save(startTime = at("21:30"), endTime = at("01:00")), InvalidEventTimeRangeException::class.java)
    }

    @Test
    fun `evento de dia completo 00-00 a 00-00 se acepta`() = runTest {
        assertTrue(save(startTime = at("00:00"), endTime = at("00:00")).isSuccess)
    }

    // endregion

    // region Editar

    @Test
    fun `editar conserva id, remoteId y estado de completado`() = runTest {
        // Regresión: al editar se perdía el remoteId y el evento se duplicaba en Google
        events.insertEvent(anEvent(id = "e1", remoteId = "google-1", isCompleted = true))

        save(id = "e1", title = "Título nuevo")

        val saved = events.events.value.getValue("e1")
        assertEquals(1, events.events.value.size)
        assertEquals("google-1", saved.remoteId)
        assertTrue(saved.isCompleted)
        assertEquals("Título nuevo", saved.title)
    }

    @Test
    fun `editar conserva las subtareas hechas, anade las nuevas pendientes y quita las eliminadas`() = runTest {
        events.insertEvent(
            anEvent(id = "e1", subtasks = listOf(Subtask("Hecha", true), Subtask("Pendiente", false), Subtask("Quitar", true)))
        )

        save(id = "e1", subtasks = listOf("Hecha", "Pendiente", "Nueva"))

        assertEquals(
            listOf(Subtask("Hecha", true), Subtask("Pendiente", false), Subtask("Nueva", false)),
            events.events.value.getValue("e1").subtasks
        )
    }

    // endregion

    @Test
    fun `sin conexion el evento se guarda igualmente`() = runTest {
        // Offline-first: el fallo de red se resuelve después con la subida pendiente
        google.isOffline = true

        val result = save()

        assertTrue(result.isSuccess)
        assertEquals(1, events.events.value.size)
    }

    private suspend fun save(
        id: String? = null,
        title: String = "Evento",
        location: String? = null,
        date: LocalDate = DAY,
        startTime: LocalTime = at("10:00"),
        endTime: LocalTime = at("11:00"),
        priority: Priority? = Priority.MEDIUM,
        subtasks: List<String> = emptyList()
    ) = saveEvent(
        id = id,
        title = title,
        description = null,
        location = location,
        date = date,
        startTime = startTime,
        endTime = endTime,
        categoryText = "Trabajo",
        categoryColor = ArgbColor(0xFF6366F1),
        priority = priority,
        subtasks = subtasks
    )

    private fun assertRejected(result: Result<Unit>, expected: Class<out Throwable>) {
        assertEquals(expected, result.exceptionOrNull()?.javaClass)
        assertTrue("No debe guardarse nada", events.events.value.isEmpty())
        assertTrue("No debe subirse nada", google.pushedEventIds.isEmpty())
    }
}
