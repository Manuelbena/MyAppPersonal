package com.syncro.presentation.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.syncro.domain.model.NextUp
import com.syncro.domain.model.SyncroItem
import com.syncro.presentation.home.components.NextUpCard
import com.syncro.presentation.home.components.startsInText
import com.syncro.presentation.home.components.tasksProgressText
import com.syncro.testutil.DAY
import com.syncro.testutil.anEvent
import com.syncro.testutil.at
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Plan de pruebas de la tarjeta "Lo próximo": los textos de cuenta atrás y lo que se ve y toca.
 *
 * Riesgos: decir "en 0 min" o redondear hacia abajo (llegar tarde), y que tocar el evento no abra su detalle.
 */
@RunWith(RobolectricTestRunner::class)
class NextUpCardTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = DAY.atTime(10, 30)

    @Test
    fun `la cuenta atras redondea hacia arriba y dice empieza ya al llegar la hora`() {
        assertEquals("en 25 min", startsInText(anEvent(startTime = at("10:55")), now))
        assertEquals("en 1 h 30 min", startsInText(anEvent(startTime = at("12:00")), now))
        assertEquals("en 1 min", startsInText(anEvent(startTime = at("10:31")), now.plusSeconds(20)))
        assertEquals("empieza ya", startsInText(anEvent(startTime = at("10:30")), now))
    }

    @Test
    fun `el progreso de tareas celebra cuando estan todas`() {
        assertEquals("2/5 tareas", tasksProgressText(2, 5))
        assertEquals("¡Tareas hechas! 🎉", tasksProgressText(3, 3))
    }

    @Test
    fun `muestra el evento en curso y el siguiente y al tocarlo abre su detalle`() {
        val meeting = anEvent(title = "Reunión", startTime = at("10:00"), endTime = at("11:00"))
        val dentist = anEvent(title = "Dentista", startTime = at("12:00"), endTime = at("13:00"), location = "Calle Mayor")
        var opened: SyncroItem.Event? = null
        compose.setContent {
            NextUpCard(NextUp(meeting, dentist, tasksDone = 1, tasksTotal = 2), now = now, onEventClick = { opened = it })
        }

        compose.onNodeWithText("Ahora · quedan 30 min").assertIsDisplayed()
        compose.onNodeWithText("12:00 · en 1 h 30 min · Calle Mayor").assertIsDisplayed()
        compose.onNodeWithText("1/2 tareas").assertIsDisplayed()
        compose.onNodeWithText("Dentista").performClick()

        assertEquals(dentist, opened)
    }

    @Test
    fun `sin eventos pendientes lo dice`() {
        compose.setContent { NextUpCard(NextUp(null, null, tasksDone = 0, tasksTotal = 1), now = now, onEventClick = {}) }

        compose.onNodeWithText("No te quedan eventos hoy").assertIsDisplayed()
    }
}
