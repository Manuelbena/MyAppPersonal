package com.syncro.presentation.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.syncro.presentation.home.components.EmptyStateView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * Plan de pruebas del título de la agenda ([dayTitle]) y del día vacío ([EmptyStateView]).
 *
 * Riesgos: un título que no deja claro qué día se mira ("Agenda del día 5" no dice ni el mes),
 * ofrecer crear cosas en un día pasado (las tareas irían a hoy) y botones que no abren lo que dicen.
 */
@RunWith(RobolectricTestRunner::class)
class EmptyDayAndTitleTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 2) // viernes

    @Test
    fun `hoy, manana y ayer se nombran asi y los demas dias con la fecha completa`() {
        assertEquals("Hoy", dayTitle(today, today))
        assertEquals("Mañana", dayTitle(today.plusDays(1), today))
        assertEquals("Ayer", dayTitle(today.minusDays(1), today))
        assertEquals("Jueves, 8 de octubre", dayTitle(LocalDate.of(2026, 10, 8), today))
    }

    @Test
    fun `un dia de otro ano lleva el ano`() {
        assertEquals("Lunes, 4 de enero de 2027", dayTitle(LocalDate.of(2027, 1, 4), today))
    }

    @Test
    fun `un dia futuro vacio ofrece crear una tarea o un evento`() {
        var tasks = 0
        var events = 0
        compose.setContent {
            EmptyStateView(date = today.plusDays(3), today = today, onAddTask = { tasks++ }, onAddEvent = { events++ })
        }

        compose.onNodeWithText("Nada planeado todavía").assertIsDisplayed()
        compose.onNodeWithText("Nueva tarea").performClick()
        compose.onNodeWithText("Nuevo evento").performClick()

        assertEquals(1, tasks)
        assertEquals(1, events)
    }

    @Test
    fun `hoy vacio invita a disfrutar del tiempo libre`() {
        compose.setContent { EmptyStateView(date = today, today = today, onAddTask = {}, onAddEvent = {}) }

        compose.onNodeWithText("Un día despejado").assertIsDisplayed()
        compose.onNodeWithText("Nueva tarea").assertIsDisplayed()
    }

    @Test
    fun `un dia pasado vacio no ofrece crear nada`() {
        compose.setContent { EmptyStateView(date = today.minusDays(2), today = today, onAddTask = {}, onAddEvent = {}) }

        compose.onNodeWithText("No hubo tareas ni eventos este día.").assertIsDisplayed()
        compose.onNodeWithText("Nueva tarea").assertDoesNotExist()
        compose.onNodeWithText("Nuevo evento").assertDoesNotExist()
    }
}
