package com.syncro.data.repository

import com.syncro.domain.model.ArgbColor
import com.syncro.data.local.SyncroDatabase
import com.syncro.data.local.dao.TaskDao
import com.syncro.testutil.DAY
import com.syncro.testutil.at
import com.syncro.testutil.aSyncedTaskEntity
import com.syncro.testutil.aTask
import com.syncro.testutil.createInMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.syncro.domain.model.SyncroItem
import kotlinx.coroutines.runBlocking
import java.time.LocalTime

/**
 * Plan de pruebas de [TaskRepositoryImpl]
 *
 * Responsabilidades: guardar tareas (con el id que asigna el dominio), consultarlas por día/rango, mapear
 * entidad <-> dominio y marcar como completadas dejando el cambio pendiente de subir.
 *
 * Riesgos principales:
 *  - Filtros de fecha que excluyen los extremos del rango.
 *  - Un cambio local que no queda marcado como pendiente se pierde en la siguiente sync.
 */
@RunWith(RobolectricTestRunner::class)
class TaskRepositoryImplTest {

    private lateinit var db: SyncroDatabase
    private lateinit var dao: TaskDao
    private lateinit var repository: TaskRepositoryImpl

    @Before
    fun setUp() {
        db = createInMemoryDatabase()
        dao = db.taskDao
        repository = TaskRepositoryImpl(dao, db.repeatSeriesDao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // region Creación (el id lo asigna el dominio: aquí solo se prueba que se respeta)

    @Test
    fun `la tarea se guarda con el id que trae`() = runTest {
        repository.insertTask(aTask(id = "mi-id"))

        assertNotNull(repository.getTaskById("mi-id"))
    }

    @Test
    fun `una tarea sin id se rechaza`() = runTest {
        // Contrato: generar ids es responsabilidad de SaveTaskUseCase. Si llega vacío es un bug
        // del llamador y es mejor fallar aquí que guardar una tarea con clave vacía
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.insertTask(aTask(id = "")) }
        }
    }

    @Test
    fun `tarea nueva queda pendiente de subir y sin remoteId`() = runTest {
        val id = save(aTask())

        val entity = dao.getTaskById(id)!!
        assertNull(entity.remoteId)
        assertEquals(1, entity.pendingChanges)
    }

    // endregion

    // region Mapeo entidad <-> dominio

    @Test
    fun `los datos de la tarea se conservan al guardar y leer`() = runTest {
        val task = aTask(
            title = "Gimnasio",
            description = "Pierna",
            time = at("18:30"),
            categoryText = "Salud",
            categoryColor = ArgbColor(0xFFFF5252)
        )

        repository.insertTask(task)
        val saved = repository.getTaskById(task.id)!!

        assertEquals("Gimnasio", saved.title)
        assertEquals("Pierna", saved.description)
        assertEquals(DAY, saved.date)
        assertEquals(at("18:30"), saved.time)
        assertEquals("Salud", saved.categoryText)
        assertEquals(ArgbColor(0xFFFF5252), saved.categoryColor)
        assertFalse(saved.isCompleted)
    }

    @Test
    fun `descripcion nula se guarda como texto vacio`() = runTest {
        val id = save(aTask(description = null))

        assertEquals("", repository.getTaskById(id)!!.description)
    }

    @Test
    fun `tarea sin categoria sigue sin color al leerla`() = runTest {
        val id = save(aTask(categoryText = null, categoryColor = null))

        val saved = repository.getTaskById(id)!!
        assertNull(saved.categoryText)
        assertNull(saved.categoryColor)
    }

    @Test
    fun `buscar una tarea inexistente devuelve null`() = runTest {
        assertNull(repository.getTaskById("no-existe"))
    }

    // endregion

    // region Formato de la hora en Room (contrato con la sync y con los datos ya guardados)

    @Test
    fun `la hora se guarda en Room como HH-mm con ceros a la izquierda`() = runTest {
        val id = save(aTask(time = at("09:05")))

        assertEquals("09:05", dao.getTaskById(id)!!.time)
    }

    @Test
    fun `una hora corrupta en la base de datos se lee como medianoche en lugar de fallar`() = runTest {
        dao.insertTask(aSyncedTaskEntity(id = "t1").copy(time = "no-es-una-hora"))

        assertEquals(LocalTime.MIDNIGHT, repository.getTaskById("t1")!!.time)
    }

    // endregion

    // region Consultas por fecha: valores límite (día anterior, extremos del rango, día siguiente)

    @Test
    fun `tareas del dia excluye otros dias y ordena por hora`() = runTest {
        save(aTask(title = "Ayer", date = DAY.minusDays(1)))
        save(aTask(title = "Tarde", time = at("18:00")))
        save(aTask(title = "Temprano", time = at("08:00")))
        save(aTask(title = "Día siguiente", date = DAY.plusDays(1)))

        val titles = repository.getTasksByDate(DAY).first().map { it.title }

        assertEquals(listOf("Temprano", "Tarde"), titles)
    }

    @Test
    fun `tareas en rango incluye los dos extremos`() = runTest {
        val start = DAY
        val end = DAY.plusDays(6)
        listOf(start.minusDays(1), start, end, end.plusDays(1)).forEach { date ->
            save(aTask(title = date.toString(), date = date))
        }

        val titles = repository.getTasksInRange(start, end).first().map { it.title }

        assertEquals(listOf(start.toString(), end.toString()), titles)
    }

    // endregion

    // region Completar: transición de estados pendiente <-> completada

    @Test
    fun `completar una tarea sincronizada la marca y la deja pendiente de subir`() = runTest {
        dao.insertTask(aSyncedTaskEntity(id = "t1"))

        repository.toggleTaskCompletion("t1")

        val entity = dao.getTaskById("t1")!!
        assertTrue(entity.isCompleted)
        // Sin esto, la siguiente descarga de Google revertiría el cambio (bug del modo sin conexión)
        assertEquals(1, entity.pendingChanges)
    }

    @Test
    fun `completar dos veces vuelve al estado original pero sigue pendiente`() = runTest {
        dao.insertTask(aSyncedTaskEntity(id = "t1"))

        repository.toggleTaskCompletion("t1")
        repository.toggleTaskCompletion("t1")

        val entity = dao.getTaskById("t1")!!
        assertFalse(entity.isCompleted)
        assertEquals(2, entity.pendingChanges)
    }

    @Test
    fun `completar una tarea inexistente no falla ni crea nada`() = runTest {
        repository.toggleTaskCompletion("no-existe")

        assertNull(dao.getTaskById("no-existe"))
    }

    // endregion

    private suspend fun save(task: SyncroItem.Task): String {
        repository.insertTask(task)
        return task.id
    }
}
