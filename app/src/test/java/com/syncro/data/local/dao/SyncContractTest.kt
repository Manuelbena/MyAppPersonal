package com.syncro.data.local.dao

import com.syncro.data.local.SyncroDatabase
import com.syncro.data.local.entity.SubtaskEntity
import com.syncro.testutil.DAY
import com.syncro.testutil.aSyncedEventEntity
import com.syncro.testutil.aSyncedTaskEntity
import com.syncro.testutil.createInMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Contrato de sincronización de [TaskDao] y [EventDao]
 *
 * GoogleSyncRepositoryImpl aún no se puede probar aislado (crea los clientes de Google por
 * dentro), pero toda su lógica sin conexión depende de estas consultas:
 *  - qué se considera pendiente de subir,
 *  - qué puede borrar una sync (nunca algo con cambios locales sin subir),
 *  - cómo se confirma una subida sin perder cambios hechos mientras tanto.
 * Si alguna de estas consultas cambia de comportamiento, se pierden datos del usuario.
 */
@RunWith(RobolectricTestRunner::class)
class SyncContractTest {

    private lateinit var db: SyncroDatabase
    private lateinit var taskDao: TaskDao
    private lateinit var eventDao: EventDao

    @Before
    fun setUp() {
        db = createInMemoryDatabase()
        taskDao = db.taskDao
        eventDao = db.eventDao
    }

    @After
    fun tearDown() {
        db.close()
    }

    // region Qué está pendiente de subir (tabla de decisión: remoteId x pendingChanges)

    @Test
    fun `pendientes de subir son las nunca subidas y las modificadas, no las sincronizadas`() = runTest {
        taskDao.insertTask(aSyncedTaskEntity(id = "nunca-subida", remoteId = null, pendingChanges = 1))
        taskDao.insertTask(aSyncedTaskEntity(id = "modificada", remoteId = "g-1", pendingChanges = 2))
        taskDao.insertTask(aSyncedTaskEntity(id = "sincronizada", remoteId = "g-2", pendingChanges = 0))
        // Datos anteriores a la migración 8->9: sin remoteId y con el contador por defecto a 0
        taskDao.insertTask(aSyncedTaskEntity(id = "antigua-sin-subir", remoteId = null, pendingChanges = 0))

        val pending = taskDao.getPendingTaskIds().toSet()

        assertEquals(setOf("nunca-subida", "modificada", "antigua-sin-subir"), pending)
    }

    @Test
    fun `eventos pendientes siguen la misma regla que las tareas`() = runTest {
        eventDao.insertEvent(aSyncedEventEntity(id = "nunca-subido", remoteId = null, pendingChanges = 1))
        eventDao.insertEvent(aSyncedEventEntity(id = "modificado", remoteId = "g-1", pendingChanges = 1))
        eventDao.insertEvent(aSyncedEventEntity(id = "sincronizado", remoteId = "g-2", pendingChanges = 0))

        assertEquals(setOf("nunca-subido", "modificado"), eventDao.getPendingEventIds().toSet())
    }

    // endregion

    // region Qué puede borrar una sincronización

    @Test
    fun `la sync solo puede borrar tareas sin cambios pendientes`() = runTest {
        taskDao.insertTask(aSyncedTaskEntity(id = "t1", remoteId = "g-limpia", pendingChanges = 0))
        taskDao.insertTask(aSyncedTaskEntity(id = "t2", remoteId = "g-pendiente", pendingChanges = 1))
        taskDao.insertTask(aSyncedTaskEntity(id = "t3", remoteId = null, pendingChanges = 1))

        assertEquals(listOf("g-limpia"), taskDao.getSyncedRemoteIds())
    }

    @Test
    fun `la sync de un rango solo considera eventos limpios que empiezan dentro`() = runTest {
        eventDao.insertEvent(aSyncedEventEntity(id = "antes", remoteId = "g-antes", date = DAY.minusDays(1)))
        eventDao.insertEvent(aSyncedEventEntity(id = "inicio", remoteId = "g-inicio", date = DAY))
        eventDao.insertEvent(aSyncedEventEntity(id = "fin", remoteId = "g-fin", date = DAY.plusDays(2)))
        eventDao.insertEvent(aSyncedEventEntity(id = "despues", remoteId = "g-despues", date = DAY.plusDays(3)))
        eventDao.insertEvent(aSyncedEventEntity(id = "pendiente", remoteId = "g-pendiente", pendingChanges = 1))

        val ids = eventDao.getSyncedRemoteIdsInRange(DAY.toEpochDay(), DAY.plusDays(2).toEpochDay())

        assertEquals(setOf("g-inicio", "g-fin"), ids.toSet())
    }

    @Test
    fun `borrar por remoteId deja intactas las demas tareas`() = runTest {
        taskDao.insertTask(aSyncedTaskEntity(id = "t1", remoteId = "g-1"))
        taskDao.insertTask(aSyncedTaskEntity(id = "t2", remoteId = "g-2"))
        taskDao.insertTask(aSyncedTaskEntity(id = "t3", remoteId = null, pendingChanges = 1))

        taskDao.deleteByRemoteIds(listOf("g-1"))

        assertNull(taskDao.getTaskById("t1"))
        assertNotNull(taskDao.getTaskById("t2"))
        assertNotNull(taskDao.getTaskById("t3"))
    }

    @Test
    fun `borrar un evento borra tambien sus subtareas`() = runTest {
        eventDao.insertEventWithSubtasks(
            aSyncedEventEntity(id = "e1"),
            listOf(SubtaskEntity(eventId = "e1", title = "A", isCompleted = false))
        )

        eventDao.deleteEventById("e1")

        assertTrue(eventDao.getSubtasksForEvent("e1").isEmpty())
    }

    // endregion

    // region Confirmar una subida (markSynced)

    @Test
    fun `confirmar una subida guarda el remoteId y limpia los pendientes`() = runTest {
        taskDao.insertTask(aSyncedTaskEntity(id = "t1", remoteId = null, pendingChanges = 1))

        taskDao.markSynced("t1", remoteId = "g-nuevo", expectedPending = 1)

        val task = taskDao.getTaskById("t1")!!
        assertEquals("g-nuevo", task.remoteId)
        assertEquals(0, task.pendingChanges)
    }

    @Test
    fun `un cambio hecho durante la subida no se pierde`() = runTest {
        // Condición de carrera: se lee la tarea (1 pendiente), se sube a Google y, antes de
        // confirmar, el usuario vuelve a tocarla (2 pendientes). Ese segundo cambio debe seguir pendiente
        taskDao.insertTask(aSyncedTaskEntity(id = "t1", remoteId = null, pendingChanges = 1))
        taskDao.toggleTaskCompletion("t1")

        taskDao.markSynced("t1", remoteId = "g-nuevo", expectedPending = 1)

        val task = taskDao.getTaskById("t1")!!
        // El remoteId se guarda siempre: si no, la próxima subida crearía un duplicado en Google
        assertEquals("g-nuevo", task.remoteId)
        assertEquals(2, task.pendingChanges)
        assertTrue(task.id in taskDao.getPendingTaskIds())
    }

    @Test
    fun `si la tarea se mueve otra vez durante la subida la marca de fecha se conserva`() = runTest {
        taskDao.insertTask(aSyncedTaskEntity(id = "t1", remoteId = "g-1"))
        taskDao.moveTask("t1", DAY.plusDays(1).toEpochDay())
        taskDao.moveTask("t1", DAY.plusDays(2).toEpochDay()) // durante la subida del primer cambio

        taskDao.markSynced("t1", remoteId = "g-1", expectedPending = 1)

        val task = taskDao.getTaskById("t1")!!
        assertTrue("La segunda fecha aún tiene que llegar a Google", task.dateChanged)
        assertEquals(DAY.plusDays(2).toEpochDay(), task.date)
    }

    @Test
    fun `las tareas sin hacer incluyen las atrasadas pero no las hechas ni las futuras`() = runTest {
        taskDao.insertTask(aSyncedTaskEntity(id = "ayer", remoteId = "g-1", date = DAY.minusDays(1)))
        taskDao.insertTask(aSyncedTaskEntity(id = "hoy", remoteId = "g-2", date = DAY))
        taskDao.insertTask(aSyncedTaskEntity(id = "hecha", remoteId = "g-3", date = DAY, isCompleted = true))
        taskDao.insertTask(aSyncedTaskEntity(id = "manana", remoteId = "g-4", date = DAY.plusDays(1)))

        val ids = taskDao.getUnfinishedTasksUntil(DAY.toEpochDay()).first().map { it.id }

        assertEquals(listOf("ayer", "hoy"), ids)
    }

    @Test
    fun `una tarea borrada no se muestra en ninguna lista pero sigue pendiente`() = runTest {
        taskDao.insertTask(aSyncedTaskEntity(id = "t1", remoteId = "g-1"))

        taskDao.markTaskDeleted("t1")

        assertTrue(taskDao.getTasksByDate(DAY.toEpochDay()).first().isEmpty())
        assertTrue(taskDao.getTasksInRange(DAY.toEpochDay(), DAY.toEpochDay()).first().isEmpty())
        assertTrue(taskDao.getUnfinishedTasksUntil(DAY.toEpochDay()).first().isEmpty())
        assertTrue("t1" in taskDao.getPendingTaskIds())
        assertTrue("La sync no debe borrarla (lo haría sin avisar a Google)", "g-1" !in taskDao.getSyncedRemoteIds())
    }

    @Test
    fun `un evento borrado no se muestra pero sigue pendiente y la sync no lo toca`() = runTest {
        eventDao.insertEvent(aSyncedEventEntity(id = "e1", remoteId = "g-1"))

        eventDao.markEventDeleted("e1")

        assertTrue(eventDao.getEventsByDate(DAY.toEpochDay()).first().isEmpty())
        assertTrue(eventDao.getEventsInRange(DAY.toEpochDay(), DAY.toEpochDay()).first().isEmpty())
        assertTrue("e1" in eventDao.getPendingEventIds())
        assertTrue("La sync no debe borrarlo (lo haría sin avisar a Google)", eventDao.getSyncedRemoteIdsInRange(DAY.toEpochDay(), DAY.toEpochDay()).isEmpty())
    }

    @Test
    fun `deshacer un borrado aun no subido vuelve a mostrar la tarea y el evento`() = runTest {
        taskDao.insertTask(aSyncedTaskEntity(id = "t1", remoteId = "g-t"))
        eventDao.insertEvent(aSyncedEventEntity(id = "e1", remoteId = "g-e"))
        taskDao.markTaskDeleted("t1")
        eventDao.markEventDeleted("e1")

        assertEquals(1, taskDao.restoreTask("t1"))
        assertEquals(1, eventDao.restoreEvent("e1"))

        assertEquals(listOf("t1"), taskDao.getTasksByDate(DAY.toEpochDay()).first().map { it.id })
        assertEquals(listOf("e1"), eventDao.getEventsByDate(DAY.toEpochDay()).first().map { it.event.id })
        // Conserva su id de Google: al subirse se actualiza, no se crea otra
        assertEquals("g-t", taskDao.getTaskById("t1")!!.remoteId)
    }

    @Test
    fun `no se puede deshacer lo que ya no esta o no estaba borrado`() = runTest {
        taskDao.insertTask(aSyncedTaskEntity(id = "t1", remoteId = "g-t"))

        assertEquals(0, taskDao.restoreTask("t1"))
        assertEquals(0, taskDao.restoreTask("ya-subida-y-quitada"))
        assertEquals(0, eventDao.restoreEvent("ya-subido-y-quitado"))
    }

    @Test
    fun `los eventos confirman la subida con la misma regla`() = runTest {
        eventDao.insertEvent(aSyncedEventEntity(id = "e1", remoteId = null, pendingChanges = 1))
        eventDao.toggleEventCompletion("e1")

        eventDao.markSynced("e1", remoteId = "g-nuevo", expectedPending = 1)

        val event = eventDao.getEventById("e1")!!
        assertEquals("g-nuevo", event.remoteId)
        assertEquals(2, event.pendingChanges)
    }

    // endregion
}
