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
import com.syncro.domain.model.SyncroItem
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
        val end: LocalTime
    )

    private var saved: Saved? = null

    private fun showForm(eventToEdit: SyncroItem.Event? = null) {
        compose.setContent {
            AddEventContent(
                eventToEdit = eventToEdit,
                onDismiss = {},
                onSave = { title, _, _, date, endDate, start, end, _, _, _, _ ->
                    saved = Saved(title, date, endDate, start, end)
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
}
