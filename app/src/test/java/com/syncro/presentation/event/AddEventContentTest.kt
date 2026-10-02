package com.syncro.presentation.event

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.performImeAction
import com.syncro.domain.model.ArgbColor
import com.syncro.domain.model.SyncroItem
import com.syncro.presentation.theme.toColor
import com.syncro.testutil.DAY
import com.syncro.testutil.anEvent
import com.syncro.testutil.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalTime

/**
 * Tests de UI del formulario de eventos: se dibuja de verdad (con Robolectric, sin móvil) y se
 * interactúa como lo haría el usuario, buscando los elementos por el texto que ve en pantalla.
 */
@RunWith(RobolectricTestRunner::class)
class AddEventContentTest {

    @get:Rule
    val compose = createComposeRule()

    /** Lo que el formulario entrega al pulsar Guardar. */
    private data class Saved(
        val title: String,
        val date: LocalDate,
        val endDate: LocalDate,
        val start: LocalTime,
        val end: LocalTime,
        val category: String = "",
        val color: Color = Color.Unspecified,
        val subtasks: List<String> = emptyList()
    )

    private var saved: Saved? = null

    private fun showForm(eventToEdit: SyncroItem.Event? = null) {
        compose.setContent {
            AddEventContent(
                eventToEdit = eventToEdit,
                onDismiss = {},
                onSave = { title, _, _, date, endDate, start, end, category, color, _, subtasks ->
                    saved = Saved(title, date, endDate, start, end, category, color, subtasks)
                }
            )
        }
    }

    @Test
    fun `sin titulo no se puede guardar`() {
        showForm()

        compose.onNodeWithText("Guardar").assertIsNotEnabled()
    }

    @Test
    fun `al escribir un titulo se puede guardar y se envian los datos`() {
        showForm()

        compose.onNodeWithText("Añade un título").performTextInput("Reunión")
        compose.onNodeWithText("Guardar").assertIsEnabled().performClick()

        assertEquals("Reunión", saved?.title)
    }

    @Test
    fun `al editar se muestran los datos del evento`() {
        showForm(anEvent(title = "Cena con Ana", location = "Casa de Ana"))

        compose.onNodeWithText("Cena con Ana").assertIsDisplayed()
        // La ubicación está más abajo: como el usuario, primero hay que desplazarse hasta ella
        compose.onNodeWithText("Casa de Ana").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `un evento que termina antes de empezar muestra el aviso y no deja guardar`() {
        showForm(anEvent(title = "Cena", startTime = at("21:30"), endTime = at("01:00")))

        compose.onNodeWithText("Si acaba al día siguiente", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Guardar").assertIsNotEnabled()
        compose.onNodeWithText("Guardar").performClick()
        assertNull(saved)
    }

    @Test
    fun `si termina al dia siguiente se puede guardar con su fecha de fin`() {
        showForm(anEvent(title = "Cena", date = DAY, endDate = DAY.plusDays(1), startTime = at("21:30"), endTime = at("01:00")))

        compose.onNodeWithText("Guardar").performClick()

        assertEquals(DAY.plusDays(1), saved?.endDate)
    }

    @Test
    fun `marcar Todo el dia guarda el evento como 00-00 a 00-00`() {
        // Regresión: el interruptor no tenía efecto y se guardaban las horas elegidas
        showForm(anEvent(title = "Vacaciones", startTime = at("10:00"), endTime = at("11:00")))

        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("Guardar").performClick()

        assertEquals(LocalTime.MIDNIGHT, saved?.start)
        assertEquals(LocalTime.MIDNIGHT, saved?.end)
    }

    @Test
    fun `Intro en el campo de subtarea la anade`() {
        showForm(anEvent(title = "Mudanza"))

        compose.onNodeWithText("Añadir subtarea").performScrollTo().performTextInput("Cajas")
        compose.onNodeWithText("Cajas").performImeAction()
        compose.onNodeWithText("Guardar").performScrollTo().performClick()

        assertEquals(listOf("Cajas"), saved?.subtasks)
    }

    // Regresión: al editar un evento de Google con categoría "General" se guardaba con el verde de Personal
    @Test
    fun `editar un evento con una categoria que no esta en la lista conserva su color`() {
        val googleBlue = ArgbColor(0xFF4285F4)
        showForm(anEvent(title = "Del calendario", categoryText = "General", categoryColor = googleBlue))

        compose.onNodeWithText("General").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Guardar").performScrollTo().performClick()

        assertEquals("General", saved?.category)
        assertEquals(googleBlue.toColor(), saved?.color)
    }

    // Regresión: a partir de las 23:00 el evento nuevo salía de 00:00 a 01:00 del mismo día (rango imposible)
    @Test
    fun `la hora por defecto es la proxima en punto y puede caer al dia siguiente`() {
        assertEquals(DAY.atTime(11, 0), nextFullHour(DAY.atTime(10, 20)))
        assertEquals(DAY.plusDays(1).atTime(0, 0), nextFullHour(DAY.atTime(23, 30)))
    }

    @Test
    fun `un evento nuevo desde otro dia empieza ese dia a la proxima hora en punto`() {
        assertEquals(DAY.plusDays(3).atTime(11, 0), defaultEventStart(DAY.atTime(10, 20), DAY.plusDays(3)))
        // Hoy o sin día: igual que antes, y a las 23:30 cae al día siguiente
        assertEquals(DAY.atTime(11, 0), defaultEventStart(DAY.atTime(10, 20), DAY))
        assertEquals(DAY.plusDays(1).atTime(0, 0), defaultEventStart(DAY.atTime(23, 30), null))
    }

    @Test
    fun `mover el inicio conserva la duracion del evento`() {
        val start = DAY.atTime(10, 0)
        val end = DAY.atTime(11, 30)

        assertEquals(DAY.atTime(18, 30), endAfterMovingStart(start, end, DAY.atTime(17, 0)))
        // Si el rango no era válido, se deja la duración por defecto: una hora
        assertEquals(DAY.atTime(18, 0), endAfterMovingStart(end, start, DAY.atTime(17, 0)))
    }

    @Test
    fun `texto de duracion`() {
        assertEquals("Dura 1 h 30 min", durationText(DAY.atTime(10, 0), DAY.atTime(11, 30), isAllDay = false))
        assertEquals("1 día", durationText(DAY.atStartOfDay(), DAY.atStartOfDay(), isAllDay = true))
        assertEquals("3 días", durationText(DAY.atStartOfDay(), DAY.plusDays(2).atStartOfDay(), isAllDay = true))
    }
}
