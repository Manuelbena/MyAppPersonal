package com.syncro.data.repository

import com.syncro.data.local.SyncroDatabase
import com.syncro.data.local.dao.EventDao
import com.syncro.data.local.dao.TaskDao
import com.syncro.data.local.entity.SubtaskEntity
import com.syncro.testutil.DAY
import com.syncro.testutil.FakeGoogleRemoteDataSource
import com.syncro.testutil.FakeGoogleRemoteDataSource.Companion.DEFAULT_LIST_ID
import com.syncro.testutil.FakeGoogleRemoteDataSource.Companion.allDayEvent
import com.syncro.testutil.FakeGoogleRemoteDataSource.Companion.googleTask
import com.syncro.testutil.FakeGoogleRemoteDataSource.Companion.timedEvent
import com.syncro.testutil.FakeSyncScheduler
import com.syncro.testutil.aSyncedEventEntity
import com.syncro.testutil.aSyncedTaskEntity
import com.syncro.testutil.at
import com.syncro.testutil.createInMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.time.Clock
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Plan de pruebas de [GoogleSyncRepositoryImpl]
 *
 * Es la pieza más delicada de la app: un fallo aquí pierde o duplica datos del usuario en su
 * cuenta de Google. Se prueba con Room real en memoria y un Google falso en memoria
 * ([FakeGoogleRemoteDataSource]); el reloj es fijo para que "hoy" sea siempre [DAY].
 *
 * Riesgos cubiertos:
 *  - Descarga: crear/actualizar lo que viene de Google sin pisar cambios locales pendientes.
 *  - Borrados: reflejar lo borrado en Google sin borrar nunca nada local por un fallo de red.
 *  - Fechas: sin desfases de zona horaria; tareas sin fecha según las reglas acordadas.
 *  - Formato: título con ✅, subtareas en la descripción, eventos de día completo.
 *  - Subida: crear o actualizar (nunca duplicar), reintentar si no hay red.
 */
@RunWith(RobolectricTestRunner::class)
class GoogleSyncRepositoryImplTest {

    private lateinit var db: SyncroDatabase
    private lateinit var taskDao: TaskDao
    private lateinit var eventDao: EventDao
    private lateinit var google: FakeGoogleRemoteDataSource
    private lateinit var scheduler: FakeSyncScheduler
    private lateinit var repository: GoogleSyncRepositoryImpl

    @Before
    fun setUp() {
        db = createInMemoryDatabase()
        taskDao = db.taskDao
        eventDao = db.eventDao
        google = FakeGoogleRemoteDataSource()
        scheduler = FakeSyncScheduler()
        repository = repositoryInZone(ZoneOffset.UTC)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun repositoryInZone(zone: ZoneId) = GoogleSyncRepositoryImpl(
        remote = google,
        taskDao = taskDao,
        eventDao = eventDao,
        syncScheduler = scheduler,
        clock = Clock.fixed(DAY.atTime(12, 0).toInstant(ZoneOffset.UTC), zone)
    )

    // region Descarga de tareas

    @Test
    fun `una tarea nueva de Google se crea en local ya sincronizada`() = runTest {
        google.addTask(googleTask("g-1", "Comprar pan", due = DAY), taskListId = "lista-casa")

        assertTrue(repository.syncTasks(force = true).isSuccess)

        val local = taskDao.getTaskByRemoteId("g-1")!!
        assertEquals("Comprar pan", local.title)
        assertEquals(DAY.toEpochDay(), local.date)
        assertEquals("lista-casa", local.taskListId)
        assertEquals(0, local.pendingChanges)
    }

    @Test
    fun `se importan las tareas de todas las listas`() = runTest {
        google.addTask(googleTask("g-1", "De la principal", due = DAY))
        google.addTask(googleTask("g-2", "De otra lista", due = DAY), taskListId = "lista-trabajo")

        repository.syncTasks(force = true)

        assertEquals(DEFAULT_LIST_ID, taskDao.getTaskByRemoteId("g-1")!!.taskListId)
        assertEquals("lista-trabajo", taskDao.getTaskByRemoteId("g-2")!!.taskListId)
    }

    @Test
    fun `los cambios hechos en Google actualizan la tarea local y conservan la hora local`() = runTest {
        // Google Tasks no guarda la hora: si se sobrescribiera, todas las tareas pasarían a 00:00
        taskDao.insertTask(aSyncedTaskEntity(id = "t1", remoteId = "g-1").copy(title = "Antiguo", time = "18:30"))
        google.addTask(googleTask("g-1", "Nuevo título", due = DAY, completedAt = "2026-09-26T10:00:00Z"))

        repository.syncTasks(force = true)

        val local = taskDao.getTaskById("t1")!!
        assertEquals("Nuevo título", local.title)
        assertTrue(local.isCompleted)
        assertEquals("18:30", local.time)
    }

    @Test
    fun `una tarea con cambios locales pendientes no se sobrescribe`() = runTest {
        taskDao.insertTask(aSyncedTaskEntity(id = "t1", remoteId = "g-1", pendingChanges = 1).copy(title = "Versión local"))
        google.addTask(googleTask("g-1", "Versión de Google", due = DAY))

        repository.syncTasks(force = true)

        assertEquals("Versión local", taskDao.getTaskById("t1")!!.title)
    }

    @Test
    fun `una tarea nueva de Google es de todo el dia porque Google no guarda la hora`() = runTest {
        google.addTask(googleTask("g-1", "Con fecha", due = DAY))
        google.addTask(googleTask("g-2", "Sin fecha"))

        repository.syncTasks(force = true)

        assertEquals("00:00", taskDao.getTaskByRemoteId("g-1")!!.time)
        assertEquals("00:00", taskDao.getTaskByRemoteId("g-2")!!.time)
    }

    // endregion

    // region Borrados

    @Test
    fun `una tarea borrada en Google se borra en local`() = runTest {
        taskDao.insertTask(aSyncedTaskEntity(id = "t1", remoteId = "g-borrada"))

        repository.syncTasks(force = true)

        assertNull(taskDao.getTaskById("t1"))
    }

    @Test
    fun `nunca se borran tareas con cambios pendientes ni las que aun no se han subido`() = runTest {
        taskDao.insertTask(aSyncedTaskEntity(id = "pendiente", remoteId = "g-borrada", pendingChanges = 1))
        taskDao.insertTask(aSyncedTaskEntity(id = "sin-subir", remoteId = null, pendingChanges = 1))

        repository.syncTasks(force = true)

        assertNotNull(taskDao.getTaskById("pendiente"))
        assertNotNull(taskDao.getTaskById("sin-subir"))
    }

    @Test
    fun `si falla la red no se borra nada en local`() = runTest {
        taskDao.insertTask(aSyncedTaskEntity(id = "t1", remoteId = "g-1"))
        google.networkError = IOException("Sin conexión")

        val result = repository.syncTasks(force = true)

        assertTrue(result.isFailure)
        assertNotNull(taskDao.getTaskById("t1"))
    }

    @Test
    fun `un evento borrado en Google se borra en local solo dentro del rango sincronizado`() = runTest {
        eventDao.insertEvent(aSyncedEventEntity(id = "dentro", remoteId = "g-dentro", date = DAY))
        eventDao.insertEvent(aSyncedEventEntity(id = "fuera", remoteId = "g-fuera", date = DAY.plusDays(10)))

        repository.syncCalendar(DAY, DAY.plusDays(2))

        assertNull(eventDao.getEventById("dentro"))
        // No se ha pedido a Google ese día: no se sabe si existe, así que no se toca
        assertNotNull(eventDao.getEventById("fuera"))
    }

    // endregion

    // region Fechas de tareas

    @Test
    fun `la fecha limite se respeta sin desfase de zona horaria`() = runTest {
        // Google envía la fecha como medianoche UTC. Convertirla a una zona con desfase negativo
        // (México, UTC-6) la movería al día anterior, que era un bug real de la versión antigua
        val repositoryInMexico = repositoryInZone(ZoneId.of("America/Mexico_City"))
        google.addTask(googleTask("g-1", "Pagar alquiler", due = DAY.plusDays(4)))

        repositoryInMexico.syncTasks(force = true)

        assertEquals(DAY.plusDays(4).toEpochDay(), taskDao.getTaskByRemoteId("g-1")!!.date)
    }

    // Regresión: se usaba LocalDate.now(zone) (hora del sistema) y no el reloj inyectado
    @Test
    fun `una tarea sin fecha y pendiente aparece hoy`() = runTest {
        google.addTask(googleTask("g-1", "Sin fecha"))

        repository.syncTasks(force = true)

        assertEquals(DAY.toEpochDay(), taskDao.getTaskByRemoteId("g-1")!!.date)
    }

    @Test
    fun `una tarea sin fecha y completada queda en el dia en que se completo`() = runTest {
        google.addTask(googleTask("g-1", "Hecha hace días", completedAt = "2026-09-20T18:00:00Z"))

        repository.syncTasks(force = true)

        assertEquals(DAY.minusDays(6).toEpochDay(), taskDao.getTaskByRemoteId("g-1")!!.date)
    }

    // endregion

    // region Límite de frecuencia

    @Test
    fun `una segunda sync de tareas seguida no vuelve a llamar a Google`() = runTest {
        repository.syncTasks()
        repository.syncTasks()

        assertEquals(1, google.calls.count { it == "listTaskLists" })
    }

    @Test
    fun `con force se sincroniza aunque se acabe de hacer`() = runTest {
        repository.syncTasks()
        repository.syncTasks(force = true)

        assertEquals(2, google.calls.count { it == "listTaskLists" })
    }

    // endregion

    // region Descarga de eventos

    @Test
    fun `un evento de Google se importa con su hora, ubicacion y categoria`() = runTest {
        google.addEvent(timedEvent("g-1", "Reunión", DAY, at("09:30"), at("10:15"), colorId = "6", location = "Oficina"))

        repository.syncCalendar(DAY)

        val local = eventDao.getEventByRemoteId("g-1")!!
        assertEquals("Reunión", local.title)
        assertEquals("09:30", local.startTime)
        assertEquals("10:15", local.endTime)
        assertEquals("Oficina", local.location)
        assertEquals("Trabajo", local.categoryText) // colorId 6 (Mandarina) = Trabajo
    }

    @Test
    fun `las categorias Deporte, Compras, Recados y Otro vuelven de Google con su categoria`() = runTest {
        val categories = listOf("Deporte", "Compras", "Recados", "Otro")
        categories.forEachIndexed { i, category ->
            eventDao.insertEventWithSubtasks(
                aSyncedEventEntity(id = "e$i", remoteId = null, pendingChanges = 1)
                    .copy(title = "Plan $i", categoryText = category),
                emptyList()
            )
            assertTrue(repository.pushEvent("e$i").isSuccess)
        }
        val colorIds = categories.indices.map { google.events.getValue(eventDao.getEventById("e$it")!!.remoteId!!).colorId }
        assertEquals(listOf("4", "3", "7", "8"), colorIds)

        repository.syncCalendar(DAY)

        val downloaded = categories.indices.map { eventDao.getEventById("e$it")!! }
        assertEquals(categories, downloaded.map { it.categoryText })
        assertEquals(categories.size, downloaded.map { it.categoryColor }.toSet().size)
    }

    @Test
    fun `un titulo con marca de completado se limpia y el evento queda completado`() = runTest {
        google.addEvent(timedEvent("g-1", "✅ Cena", DAY, at("21:00"), at("22:00")))

        repository.syncCalendar(DAY)

        val local = eventDao.getEventByRemoteId("g-1")!!
        assertEquals("Cena", local.title)
        assertTrue(local.isCompleted)
    }

    @Test
    fun `las subtareas se leen de la descripcion y se separan del texto`() = runTest {
        val description = "Llevar postre\n\nSubtareas:\n- [x] Comprar tarta\n- [ ] Llamar a Ana"
        google.addEvent(timedEvent("g-1", "Cumpleaños", DAY, at("18:00"), at("20:00"), description = description))

        repository.syncCalendar(DAY)

        val local = eventDao.getEventByRemoteId("g-1")!!
        assertEquals("Llevar postre", local.description)
        assertEquals(
            listOf("Comprar tarta" to true, "Llamar a Ana" to false),
            eventDao.getSubtasksForEvent(local.id).map { it.title to it.isCompleted }
        )
    }

    @Test
    fun `un evento de dia completo se importa en su fecha como 00-00 a 00-00`() = runTest {
        google.addEvent(allDayEvent("g-1", "Vacaciones", DAY))

        repository.syncCalendar(DAY)

        val local = eventDao.getEventByRemoteId("g-1")!!
        assertEquals(DAY.toEpochDay(), local.date)
        assertEquals("00:00", local.startTime)
        assertEquals("00:00", local.endTime)
    }

    @Test
    fun `un evento con cambios locales pendientes no se sobrescribe`() = runTest {
        eventDao.insertEvent(aSyncedEventEntity(id = "e1", remoteId = "g-1", pendingChanges = 1).copy(title = "Versión local"))
        google.addEvent(timedEvent("g-1", "Versión de Google", DAY, at("10:00"), at("11:00")))

        repository.syncCalendar(DAY)

        assertEquals("Versión local", eventDao.getEventById("e1")!!.title)
    }

    // endregion

    // region Subida de tareas

    @Test
    fun `subir una tarea nueva la crea en la lista principal con su fecha`() = runTest {
        taskDao.insertTask(aSyncedTaskEntity(id = "t1", remoteId = null, pendingChanges = 1).copy(title = "Nueva"))

        assertTrue(repository.pushTask("t1").isSuccess)

        val local = taskDao.getTaskById("t1")!!
        val remote = google.task(local.remoteId!!)!!
        assertEquals("Nueva", remote.title)
        assertEquals("${DAY}T00:00:00.000Z", remote.due)
        assertEquals(0, local.pendingChanges)
    }

    @Test
    fun `subir una tarea existente la actualiza en su lista sin duplicarla ni cambiar su fecha`() = runTest {
        google.addTask(googleTask("g-1", "Sin fecha en Google"), taskListId = "lista-trabajo")
        taskDao.insertTask(
            aSyncedTaskEntity(id = "t1", remoteId = "g-1", isCompleted = true, pendingChanges = 1)
                .copy(title = "Sin fecha en Google", taskListId = "lista-trabajo")
        )

        repository.pushTask("t1")

        assertEquals(listOf("patchTask(lista-trabajo, g-1)"), google.calls)
        val remote = google.task("g-1")!!
        assertEquals("completed", remote.status)
        // En local la tarea está en "hoy", pero en Google no tenía fecha: el patch no debe añadirla
        assertNull(remote.due)
    }

    @Test
    fun `pasar una tarea a otro dia manda la nueva fecha a Google y la marca se limpia`() = runTest {
        google.addTask(googleTask("g-1", "Llamar al banco", due = DAY))
        taskDao.insertTask(aSyncedTaskEntity(id = "t1", remoteId = "g-1"))

        taskDao.moveTask("t1", DAY.plusDays(1).toEpochDay())
        repository.pushTask("t1")

        assertEquals("${DAY.plusDays(1)}T00:00:00.000Z", google.task("g-1")!!.due)
        val local = taskDao.getTaskById("t1")!!
        assertEquals(0, local.pendingChanges)
        assertFalse(local.dateChanged)
    }

    @Test
    fun `una tarea pasada a otro dia no vuelve a su fecha antigua al sincronizar`() = runTest {
        google.addTask(googleTask("g-1", "Llamar al banco", due = DAY))
        taskDao.insertTask(aSyncedTaskEntity(id = "t1", remoteId = "g-1"))

        taskDao.moveTask("t1", DAY.plusDays(1).toEpochDay())
        repository.pushTask("t1")
        repository.syncTasks(force = true)

        assertEquals(DAY.plusDays(1).toEpochDay(), taskDao.getTaskById("t1")!!.date)
    }

    @Test
    fun `pasar de dia una tarea sin conexion la deja pendiente con la marca de fecha`() = runTest {
        google.addTask(googleTask("g-1", "Llamar al banco", due = DAY))
        taskDao.insertTask(aSyncedTaskEntity(id = "t1", remoteId = "g-1"))
        google.networkError = IOException("Sin conexión")

        taskDao.moveTask("t1", DAY.plusDays(1).toEpochDay())
        repository.pushTask("t1")

        val local = taskDao.getTaskById("t1")!!
        assertTrue(local.dateChanged)
        assertTrue("t1" in taskDao.getPendingTaskIds())
    }

    @Test
    fun `descompletar una tarea borra su fecha de completado en Google`() = runTest {
        google.addTask(googleTask("g-1", "Tarea", due = DAY, completedAt = "2026-09-25T10:00:00Z"))
        taskDao.insertTask(aSyncedTaskEntity(id = "t1", remoteId = "g-1", isCompleted = false, pendingChanges = 1))

        repository.pushTask("t1")

        val remote = google.task("g-1")!!
        assertEquals("needsAction", remote.status)
        assertNull(remote.completed)
    }

    @Test
    fun `si la tarea se borro en Google mientras se editaba, gana el borrado`() = runTest {
        taskDao.insertTask(aSyncedTaskEntity(id = "t1", remoteId = "g-que-ya-no-existe", pendingChanges = 1))

        assertTrue(repository.pushTask("t1").isSuccess)

        assertNull(taskDao.getTaskById("t1"))
    }

    @Test
    fun `sin conexion la subida falla, queda pendiente y se programa el reintento`() = runTest {
        taskDao.insertTask(aSyncedTaskEntity(id = "t1", remoteId = null, pendingChanges = 1))
        google.networkError = IOException("Sin conexión")

        val result = repository.pushTask("t1")

        assertTrue(result.isFailure)
        assertEquals(1, scheduler.scheduledPushes)
        assertTrue("t1" in taskDao.getPendingTaskIds())
    }

    // endregion

    // region Subida de eventos

    @Test
    fun `subir un evento nuevo lo crea con sus subtareas en la descripcion`() = runTest {
        eventDao.insertEventWithSubtasks(
            aSyncedEventEntity(id = "e1", remoteId = null, pendingChanges = 1)
                .copy(title = "Cumpleaños", description = "Llevar postre", categoryText = "Ocio", startTime = "18:00", endTime = "20:00"),
            listOf(
                SubtaskEntity(eventId = "e1", title = "Comprar tarta", isCompleted = true),
                SubtaskEntity(eventId = "e1", title = "Llamar a Ana", isCompleted = false)
            )
        )

        assertTrue(repository.pushEvent("e1").isSuccess)

        val local = eventDao.getEventById("e1")!!
        val remote = google.events.getValue(local.remoteId!!)
        assertEquals("Cumpleaños", remote.summary)
        assertEquals("Llevar postre\n\nSubtareas:\n- [x] Comprar tarta\n- [ ] Llamar a Ana", remote.description)
        assertEquals("5", remote.colorId) // Ocio = Plátano
        assertEquals(DAY.atTime(18, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), remote.start.dateTime.value)
        assertEquals(0, local.pendingChanges)
    }

    @Test
    fun `un evento completado se sube con la marca en el titulo`() = runTest {
        eventDao.insertEvent(aSyncedEventEntity(id = "e1", remoteId = null, pendingChanges = 1).copy(title = "Cena", isCompleted = true))

        repository.pushEvent("e1")

        assertEquals("✅ Cena", google.events.values.single().summary)
    }

    @Test
    fun `un evento de dia completo se sube como fecha sin hora con fin exclusivo`() = runTest {
        eventDao.insertEvent(aSyncedEventEntity(id = "e1", remoteId = null, pendingChanges = 1).copy(startTime = "00:00", endTime = "00:00"))

        repository.pushEvent("e1")

        val remote = google.events.values.single()
        assertNull(remote.start.dateTime)
        assertEquals(DAY.toString(), remote.start.date.toStringRfc3339())
        assertEquals(DAY.plusDays(1).toString(), remote.end.date.toStringRfc3339())
    }

    @Test
    fun `un evento que termina antes de empezar no se envia y sigue pendiente`() = runTest {
        // Caso real: "Cenar con mi amigo" de 21:30 a 01:00. Google lo rechazaría siempre (400)
        eventDao.insertEvent(aSyncedEventEntity(id = "e1", remoteId = null, pendingChanges = 1).copy(startTime = "21:30", endTime = "01:00"))

        assertTrue(repository.pushEvent("e1").isSuccess)

        assertTrue(google.calls.isEmpty())
        assertTrue("e1" in eventDao.getPendingEventIds())
    }

    // endregion

    // region Subida de todo lo pendiente

    @Test
    fun `se suben los pendientes de cualquier fecha y un fallo no bloquea al resto`() = runTest {
        taskDao.insertTask(aSyncedTaskEntity(id = "t-ayer", remoteId = null, date = DAY.minusDays(1), pendingChanges = 1).copy(title = "Ayer"))
        taskDao.insertTask(aSyncedTaskEntity(id = "t-falla", remoteId = null, pendingChanges = 1).copy(title = "Rechazada"))
        eventDao.insertEvent(aSyncedEventEntity(id = "e-mes-que-viene", remoteId = null, date = DAY.plusMonths(1), pendingChanges = 1))
        google.failingTitles += "Rechazada"

        val result = repository.pushPendingChanges()

        assertTrue(result.isFailure)
        assertEquals(1, scheduler.scheduledPushes)
        assertEquals(setOf("t-falla"), taskDao.getPendingTaskIds().toSet())
        assertTrue(eventDao.getPendingEventIds().isEmpty())
    }

    // endregion

    // region Eventos que cruzan la medianoche o duran varios días

    @Test
    fun `un evento de Google que cruza la medianoche se importa con su fecha de fin`() = runTest {
        google.addEvent(timedEvent("g-1", "Cena", DAY, at("21:30"), at("01:00"), endDate = DAY.plusDays(1)))

        repository.syncCalendar(DAY)

        val local = eventDao.getEventByRemoteId("g-1")!!
        assertEquals(DAY.toEpochDay(), local.date)
        assertEquals(DAY.plusDays(1).toEpochDay(), local.endDate)
        assertEquals("01:00", local.endTime)
    }

    @Test
    fun `sincronizar el dia siguiente no duplica ni borra un evento que empezo el dia anterior`() = runTest {
        google.addEvent(timedEvent("g-1", "Cena", DAY, at("21:30"), at("01:00"), endDate = DAY.plusDays(1)))
        repository.syncCalendar(DAY)
        val localId = eventDao.getEventByRemoteId("g-1")!!.id

        // Google devuelve el evento también al pedir el día 27, porque lo ocupa en parte
        repository.syncCalendar(DAY.plusDays(1))

        assertEquals(localId, eventDao.getEventByRemoteId("g-1")!!.id)
        assertEquals(1, eventDao.getEventsByDate(DAY.plusDays(1).toEpochDay()).first().size)
    }

    @Test
    fun `un evento de dia completo de varios dias se importa con su ultimo dia incluido`() = runTest {
        google.addEvent(allDayEvent("g-1", "Vacaciones", DAY, lastDay = DAY.plusDays(4)))

        repository.syncCalendar(DAY)

        assertEquals(DAY.plusDays(4).toEpochDay(), eventDao.getEventByRemoteId("g-1")!!.endDate)
    }

    @Test
    fun `un evento que cruza la medianoche se sube terminando al dia siguiente`() = runTest {
        eventDao.insertEvent(
            aSyncedEventEntity(id = "e1", remoteId = null, endDate = DAY.plusDays(1), pendingChanges = 1)
                .copy(startTime = "21:30", endTime = "01:00")
        )

        assertTrue(repository.pushEvent("e1").isSuccess)

        val remote = google.events.values.single()
        assertEquals(DAY.plusDays(1).atTime(1, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), remote.end.dateTime.value)
    }

    @Test
    fun `un evento de dia completo de varios dias se sube con fin exclusivo`() = runTest {
        eventDao.insertEvent(
            aSyncedEventEntity(id = "e1", remoteId = null, endDate = DAY.plusDays(2), pendingChanges = 1)
                .copy(startTime = "00:00", endTime = "00:00")
        )

        repository.pushEvent("e1")

        assertEquals(DAY.plusDays(3).toString(), google.events.values.single().end.date.toStringRfc3339())
    }

    // endregion

    // region Ida y vuelta

    @Test
    fun `lo que la app sube a Google lo vuelve a leer igual`() = runTest {
        // Si el formato de subida y el de lectura se desincronizan, cada sync corrompería los datos
        eventDao.insertEventWithSubtasks(
            aSyncedEventEntity(id = "e1", remoteId = null, pendingChanges = 1)
                .copy(title = "Mudanza", description = "Piso nuevo", isCompleted = true, startTime = "09:00", endTime = "14:00"),
            listOf(
                SubtaskEntity(eventId = "e1", title = "Cajas", isCompleted = true),
                SubtaskEntity(eventId = "e1", title = "Furgoneta", isCompleted = false)
            )
        )
        repository.pushEvent("e1")

        repository.syncCalendar(DAY)

        val local = eventDao.getEventById("e1")!!
        assertEquals("Mudanza", local.title)
        assertEquals("Piso nuevo", local.description)
        assertTrue(local.isCompleted)
        assertEquals("09:00", local.startTime)
        assertEquals("14:00", local.endTime)
        assertEquals(
            listOf("Cajas" to true, "Furgoneta" to false),
            eventDao.getSubtasksForEvent("e1").map { it.title to it.isCompleted }
        )
        assertEquals("No debe duplicarse al volver a leerlo", 1, eventDao.getEventsByDate(DAY.toEpochDay()).first().size)
        assertTrue(eventDao.getPendingEventIds().isEmpty())
    }

    // endregion
}
