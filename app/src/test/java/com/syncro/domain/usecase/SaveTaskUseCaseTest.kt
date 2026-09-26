package com.syncro.domain.usecase

import com.syncro.domain.model.BlankTitleException
import com.syncro.testutil.CallLog
import com.syncro.testutil.DAY
import com.syncro.testutil.FakeGoogleSyncRepository
import com.syncro.testutil.FakeTaskRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Plan de pruebas de [SaveTaskUseCase]
 *
 * Reglas: título obligatorio, id único asignado por el dominio, se guarda en local antes de
 * subir y un fallo de red no impide guardar.
 */
class SaveTaskUseCaseTest {

    private lateinit var log: CallLog
    private lateinit var tasks: FakeTaskRepository
    private lateinit var google: FakeGoogleSyncRepository
    private lateinit var saveTask: SaveTaskUseCase

    @Before
    fun setUp() {
        log = CallLog()
        tasks = FakeTaskRepository(log)
        google = FakeGoogleSyncRepository(log)
        saveTask = SaveTaskUseCase(tasks, google)
    }

    @Test
    fun `crear una tarea la guarda pendiente de completar y la sube a Google`() = runTest {
        val result = saveTask(title = "Comprar pan", description = "", date = DAY, time = "09:00")

        assertTrue(result.isSuccess)
        val saved = tasks.tasks.value.values.single()
        assertEquals("Comprar pan", saved.title)
        assertEquals(DAY, saved.date)
        assertEquals("09:00", saved.time)
        assertFalse(saved.isCompleted)
        assertEquals(listOf("insertTask(${saved.id})", "pushTask(${saved.id})"), log.calls)
    }

    @Test
    fun `dos tareas identicas creadas seguidas no se pisan`() = runTest {
        // Regresión: el id antiguo (fecha_titulo_hora) hacía que la segunda sustituyera a la primera
        saveTask(title = "Comprar pan", description = "", date = DAY, time = "10:00")
        saveTask(title = "Comprar pan", description = "", date = DAY, time = "10:00")

        assertEquals(2, tasks.tasks.value.size)
    }

    @Test
    fun `titulo vacio se rechaza sin guardar ni subir nada`() = runTest {
        val result = saveTask(title = "  ", description = "", date = DAY, time = "10:00")

        assertTrue(result.exceptionOrNull() is BlankTitleException)
        assertTrue(tasks.tasks.value.isEmpty())
        assertTrue(google.pushedTaskIds.isEmpty())
    }

    @Test
    fun `sin conexion la tarea se guarda igualmente`() = runTest {
        google.isOffline = true

        val result = saveTask(title = "Llamar", description = "", date = DAY, time = "10:00")

        assertTrue(result.isSuccess)
        assertEquals(1, tasks.tasks.value.size)
    }
}
