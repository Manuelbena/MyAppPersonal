package com.syncro.presentation.notes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.syncro.testutil.aNote
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Detalle de una nota: su contenido completo y que borrar pida confirmación (no se puede deshacer). */
@RunWith(RobolectricTestRunner::class)
class NoteDetailContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show() {
        compose.setContent {
            NoteDetailContent(
                note = aNote(title = "Ideas regalo", content = "Libro de cocina\nAuriculares"),
                onEdit = { calls += "edit" },
                onDelete = { calls += "delete" }
            )
        }
    }

    @Test
    fun `muestra el titulo, todo el contenido y cuando se creo`() {
        show()

        compose.onNodeWithText("Ideas regalo").assertIsDisplayed()
        compose.onNodeWithText("Libro de cocina\nAuriculares").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sábado, 26 de septiembre").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("10:00").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `editar avisa a quien muestra el detalle`() {
        show()

        compose.onNodeWithText("Editar").performClick()

        assertEquals(listOf("edit"), calls)
    }

    @Test
    fun `borrar pide confirmacion antes de borrar`() {
        show()

        compose.onNodeWithText("Borrar").performClick()
        assertEquals(emptyList<String>(), calls)

        compose.onNodeWithText("¿Borrar?").performClick()
        assertEquals(listOf("delete"), calls)
    }
}
