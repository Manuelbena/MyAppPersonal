package com.syncro.domain.usecase

import com.syncro.domain.model.ArgbColor
import com.syncro.domain.model.BlankTitleException
import com.syncro.testutil.FakeNoteRepository
import com.syncro.testutil.aNote
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Plan de pruebas de [SaveNoteUseCase]
 *
 * Reglas: título obligatorio, id asignado por el dominio, fecha de creación = "ahora" al crear
 * y la original al editar (es la que ordena la lista de notas).
 *
 * El reloj es fijo: un test que usa la hora real no puede comprobar la fecha exacta y además
 * puede fallar o no según el momento en que se ejecute.
 */
class SaveNoteUseCaseTest {

    private val now = LocalDateTime.of(2026, 9, 26, 18, 0)
    private val fixedClock = Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    private lateinit var notes: FakeNoteRepository
    private lateinit var saveNote: SaveNoteUseCase

    @Before
    fun setUp() {
        notes = FakeNoteRepository()
        saveNote = SaveNoteUseCase(notes, fixedClock)
    }

    @Test
    fun `crear una nota la guarda con id nuevo y fecha de creacion actual`() = runTest {
        val result = saveNote(title = "Ideas", content = "App de hábitos", color = COLOR)

        assertTrue(result.isSuccess)
        val saved = notes.notes.value.values.single()
        assertEquals("Ideas", saved.title)
        assertEquals(now, saved.createdAt)
    }

    @Test
    fun `dos notas iguales creadas seguidas tienen ids distintos`() = runTest {
        saveNote(title = "Idea", content = "", color = COLOR)
        saveNote(title = "Idea", content = "", color = COLOR)

        assertEquals(2, notes.notes.value.size)
    }

    @Test
    fun `editar una nota conserva su fecha de creacion`() = runTest {
        // Regresión: al editar se ponía createdAt = ahora y la nota saltaba al principio de la lista
        val createdLastMonth = LocalDateTime.of(2026, 8, 1, 9, 0)
        notes.insertNote(aNote(id = "n1", title = "Original", createdAt = createdLastMonth))

        saveNote(id = "n1", title = "Editada", content = "Nuevo contenido", color = COLOR)

        val saved = notes.notes.value.getValue("n1")
        assertEquals("Editada", saved.title)
        assertEquals(createdLastMonth, saved.createdAt)
    }

    @Test
    fun `editar con un id que ya no existe la crea con fecha actual`() = runTest {
        // Caso límite: la nota se borró en otra pantalla mientras se editaba
        saveNote(id = "borrada", title = "Recuperada", content = "", color = COLOR)

        assertEquals(now, notes.notes.value.getValue("borrada").createdAt)
    }

    @Test
    fun `titulo vacio se rechaza sin guardar nada`() = runTest {
        val result = saveNote(title = "   ", content = "Contenido sin título", color = COLOR)

        assertTrue(result.exceptionOrNull() is BlankTitleException)
        assertTrue(notes.notes.value.isEmpty())
    }

    @Test
    fun `los espacios alrededor del titulo se eliminan`() = runTest {
        saveNote(title = "  Ideas  ", content = "", color = COLOR)

        assertEquals("Ideas", notes.notes.value.values.single().title)
    }

    private companion object {
        val COLOR = ArgbColor(0xFFF59E0B)
    }
}
