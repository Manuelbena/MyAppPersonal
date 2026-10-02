package com.syncro.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.syncro.testutil.DAY
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Migraciones de la base de datos: lo que protege los datos de quien actualiza la app.
 *
 * Se crea una base de datos idéntica a la versión 9 ([Schema9]), se rellena con datos y se abre
 * con la versión actual. Room comprueba al abrir que el esquema migrado es exactamente el que
 * esperan las entidades, y aquí NO hay fallbackToDestructiveMigration: si algo no cuadra, el test
 * falla en vez de borrar todo como pasaría en el móvil.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var database: SyncroDatabase? = null

    @Before
    fun setUp() {
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `migrar de la version 9 conserva todos los datos y deja el esquema que espera Room`() = runTest {
        createVersion9Database {
            execSQL("INSERT INTO tasks (id, remoteId, taskListId, title, description, date, time, isCompleted, pendingChanges) VALUES ('t1', 'g-t1', 'lista', 'Comprar pan', '', ${DAY.toEpochDay()}, '09:00', 0, 0)")
            execSQL("INSERT INTO notes (id, title, content, color, createdAt) VALUES ('n1', 'Ideas', 'Contenido', -1, 1790000000000)")
            execSQL("INSERT INTO user_profile (email, name) VALUES ('ana@example.com', 'Ana')")
            insertEvent(id = "e1", startTime = "10:00", endTime = "11:00")
            execSQL("INSERT INTO subtasks (eventId, title, isCompleted) VALUES ('e1', 'Preparar', 1)")
        }

        val db = openCurrentVersion()

        assertEquals("Comprar pan", db.taskDao.getTaskById("t1")!!.title)
        assertNotNull(db.noteDao.getNoteById("n1"))
        assertEquals("ana@example.com", db.userDao.getUser().first()!!.email)
        assertEquals(listOf("Preparar"), db.eventDao.getSubtasksForEvent("e1").map { it.title })
    }

    @Test
    fun `un evento normal pasa a terminar el mismo dia`() = runTest {
        createVersion9Database { insertEvent(id = "e1", startTime = "10:00", endTime = "11:00") }

        val event = openCurrentVersion().eventDao.getEventById("e1")!!

        assertEquals(event.date, event.endDate)
    }

    @Test
    fun `un evento que terminaba antes de empezar pasa a terminar al dia siguiente y se subira`() = runTest {
        // Caso real del usuario: "Cenar con mi amigo" 21:30 → 01:00 guardado en un solo día,
        // rechazado por Google y atascado sin subir
        createVersion9Database { insertEvent(id = "cena", startTime = "21:30", endTime = "01:00", pendingChanges = 2) }

        val db = openCurrentVersion()

        val event = db.eventDao.getEventById("cena")!!
        assertEquals(DAY.plusDays(1).toEpochDay(), event.endDate)
        assertTrue("Debe seguir pendiente para subirse", "cena" in db.eventDao.getPendingEventIds())
    }

    @Test
    fun `migrar de la version 10 conserva las tareas y sus cambios pendientes, sin marcar la fecha`() = runTest {
        createDatabase(Schema10.CREATE_STATEMENTS, Schema10.VERSION) {
            execSQL("INSERT INTO tasks (id, remoteId, taskListId, title, description, date, time, isCompleted, pendingChanges) VALUES ('t1', 'g-t1', NULL, 'Llamar al banco', '', ${DAY.toEpochDay()}, '17:00', 0, 2)")
        }

        val task = openCurrentVersion().taskDao.getTaskById("t1")!!

        assertEquals("Llamar al banco", task.title)
        assertEquals(2, task.pendingChanges)
        // Las tareas existentes no cambiaron de fecha en la app: el patch no debe tocar su fecha en Google
        assertFalse(task.dateChanged)
    }

    @Test
    fun `migrar de la version 11 conserva los eventos, ninguno borrado`() = runTest {
        createDatabase(Schema11.CREATE_STATEMENTS, Schema11.VERSION) { insertEvent(id = "e1", startTime = "10:00", endTime = "11:00") }

        val event = openCurrentVersion().eventDao.getEventById("e1")!!

        assertFalse(event.isDeleted)
    }

    @Test
    fun `migrar de la version 12 conserva las tareas, ninguna borrada`() = runTest {
        createDatabase(Schema12.CREATE_STATEMENTS, Schema12.VERSION) {
            execSQL("INSERT INTO tasks (id, remoteId, taskListId, title, description, date, time, isCompleted, pendingChanges, dateChanged) VALUES ('t1', 'g-t1', NULL, 'Llamar al banco', '', ${DAY.toEpochDay()}, '17:00', 0, 0, 0)")
        }

        val task = openCurrentVersion().taskDao.getTaskById("t1")!!

        assertEquals("Llamar al banco", task.title)
        assertFalse(task.isDeleted)
    }

    @Test
    fun `migrar de la version 13 conserva todo y crea Ahorros vacio`() = runTest {
        createDatabase(Schema13.CREATE_STATEMENTS, Schema13.VERSION) {
            execSQL("INSERT INTO notes (id, title, content, color, createdAt) VALUES ('n1', 'Ideas', 'Contenido', -1, 1790000000000)")
        }

        val db = openCurrentVersion()

        assertNotNull(db.noteDao.getNoteById("n1"))
        assertEquals(0, db.movementDao.count())
    }

    @Test
    fun `migrar de la version 14 conserva los movimientos y crea presupuestos vacios`() = runTest {
        createDatabase(Schema14.CREATE_STATEMENTS, Schema14.VERSION) {
            execSQL("INSERT INTO movements (id, type, amountCents, category, date, note, repeatsMonthly) VALUES ('m1', 'EXPENSE', 4590, 'GROCERIES', ${DAY.toEpochDay()}, 'Mercadona', 0)")
        }

        val db = openCurrentVersion()

        assertEquals(1, db.movementDao.count())
        assertTrue(db.budgetDao.observeBudgets().first().isEmpty())
    }

    private fun createVersion9Database(seed: SQLiteDatabase.() -> Unit) =
        createDatabase(Schema9.CREATE_STATEMENTS, Schema9.VERSION, seed)

    private fun createDatabase(statements: List<String>, version: Int, seed: SQLiteDatabase.() -> Unit) {
        val file = context.getDatabasePath(DB_NAME)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            statements.forEach(db::execSQL)
            db.seed()
            db.version = version
        }
    }

    private fun SQLiteDatabase.insertEvent(id: String, startTime: String, endTime: String, pendingChanges: Int = 0) {
        execSQL(
            "INSERT INTO events (id, remoteId, title, description, date, startTime, endTime, categoryText, categoryColor, " +
                "priority, isAllDay, location, notificationEnabled, isCompleted, pendingChanges) VALUES " +
                "('$id', NULL, 'Evento', NULL, ${DAY.toEpochDay()}, '$startTime', '$endTime', 'Ocio', -1, 'MEDIUM', 0, NULL, 1, 0, $pendingChanges)"
        )
    }

    /** Igual que en AppModule pero sin fallbackToDestructiveMigration: un fallo debe verse. */
    private fun openCurrentVersion(): SyncroDatabase =
        Room.databaseBuilder(context, SyncroDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
