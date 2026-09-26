package com.syncro.data.repository

import com.syncro.data.local.SyncroDatabase
import com.syncro.testutil.aNote
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
import java.time.LocalDateTime

/**
 * Plan de pruebas de [NoteRepositoryImpl]
 *
 * Responsabilidades: guardar (crear o editar por id), listar de más reciente a más antigua,
 * borrar y mapear fecha/color. Las notas son solo locales: no se sincronizan con Google.
 */
@RunWith(RobolectricTestRunner::class)
class NoteRepositoryImplTest {

    private lateinit var db: SyncroDatabase
    private lateinit var repository: NoteRepositoryImpl

    @Before
    fun setUp() {
        db = createInMemoryDatabase()
        repository = NoteRepositoryImpl(db.noteDao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `los datos de la nota se conservan al guardar y leer`() = runTest {
        val note = aNote(id = "n1", title = "Ideas", content = "App de hábitos")

        repository.insertNote(note)

        assertEquals(note, repository.getNoteById("n1"))
    }

    @Test
    fun `la fecha de creacion se guarda con precision de milisegundos`() = runTest {
        // Valor límite de precisión: se guarda como epoch millis, así que los nanosegundos se pierden.
        // Si alguien compara notas por igualdad con LocalDateTime.now(), fallará por esto
        val createdAt = LocalDateTime.of(2026, 9, 26, 10, 0, 0, 123_456_789)

        repository.insertNote(aNote(id = "n1", createdAt = createdAt))

        assertEquals(createdAt.withNano(123_000_000), repository.getNoteById("n1")!!.createdAt)
    }

    @Test
    fun `guardar con un id existente edita la nota en lugar de duplicarla`() = runTest {
        repository.insertNote(aNote(id = "n1", title = "Original"))

        repository.insertNote(aNote(id = "n1", title = "Editada"))

        val notes = repository.getAllNotes().first()
        assertEquals(1, notes.size)
        assertEquals("Editada", notes.single().title)
    }

    @Test
    fun `las notas se listan de la mas reciente a la mas antigua`() = runTest {
        repository.insertNote(aNote(id = "vieja", createdAt = LocalDateTime.of(2026, 1, 1, 9, 0)))
        repository.insertNote(aNote(id = "nueva", createdAt = LocalDateTime.of(2026, 9, 1, 9, 0)))
        repository.insertNote(aNote(id = "media", createdAt = LocalDateTime.of(2026, 5, 1, 9, 0)))

        val ids = repository.getAllNotes().first().map { it.id }

        assertEquals(listOf("nueva", "media", "vieja"), ids)
    }

    @Test
    fun `borrar una nota elimina solo esa`() = runTest {
        repository.insertNote(aNote(id = "n1"))
        repository.insertNote(aNote(id = "n2"))

        repository.deleteNote(aNote(id = "n1"))

        assertNull(repository.getNoteById("n1"))
        assertEquals(listOf("n2"), repository.getAllNotes().first().map { it.id })
    }

    @Test
    fun `borrar una nota inexistente no falla`() = runTest {
        repository.deleteNote(aNote(id = "no-existe"))

        assertEquals(0, repository.getAllNotes().first().size)
    }
}
