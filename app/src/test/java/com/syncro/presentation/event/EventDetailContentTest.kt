package com.syncro.presentation.event

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.syncro.domain.model.Subtask
import com.syncro.domain.model.SyncroItem
import com.syncro.testutil.DAY
import com.syncro.testutil.anEvent
import com.syncro.testutil.at
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalTime

/**
 * Detalle de un evento: debe enseñar todos sus datos (lo que la tarjeta recorta) y sus acciones
 * deben llegar a quien lo muestra. Los textos de fecha se prueban aparte porque tienen casos
 * (todo el día, varios días, cruzar medianoche) que se equivocan fácil.
 */
@RunWith(RobolectricTestRunner::class)
class EventDetailContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(event: SyncroItem.Event) {
        compose.setContent {
            EventDetailContent(
                event = event,
                onToggleCompleted = { calls += "toggle" },
                onSubtaskToggle = { calls += "subtask:$it" },
                onEdit = { calls += "edit" },
                onShare = { calls += "share" }
            )
        }
    }

    @Test
    fun `muestra todos los datos del evento`() {
        show(
            anEvent(
                title = "Cena de equipo",
                description = "Reservado a nombre de Ana",
                location = "Calle Mayor 5, Madrid",
                categoryText = "Ocio",
                startTime = at("21:00"),
                endTime = at("23:30"),
                remoteId = "g-1",
                subtasks = listOf(Subtask("Confirmar mesa", isCompleted = true), Subtask("Llevar regalo", isCompleted = false))
            )
        )

        compose.onNodeWithText("Cena de equipo").assertIsDisplayed()
        compose.onNodeWithText("Ocio").assertIsDisplayed()
        compose.onNodeWithText("Sábado, 26 de septiembre").assertIsDisplayed()
        compose.onNodeWithText("21:00 – 23:30 · 2 h 30 min").assertIsDisplayed()
        compose.onNodeWithText("Calle Mayor 5, Madrid").assertIsDisplayed()
        compose.onNodeWithText("Reservado a nombre de Ana").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1/2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Llevar regalo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sincronizado con Google").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `las acciones y las subtareas avisan a quien muestra el detalle`() {
        show(anEvent(subtasks = listOf(Subtask("Comprar pan", isCompleted = false))))

        compose.onNodeWithText("Completar").performClick()
        compose.onNodeWithText("Editar").performClick()
        compose.onNodeWithText("Compartir").performClick()
        compose.onNodeWithText("Comprar pan").performScrollTo().performClick()

        assertEquals(listOf("toggle", "edit", "share", "subtask:Comprar pan"), calls)
    }

    @Test
    fun `un evento completado ofrece volver a pendiente`() {
        show(anEvent(isCompleted = true, remoteId = null))

        compose.onNodeWithText("Completado").assertIsDisplayed()
        compose.onNodeWithText("Pendiente").performClick()
        compose.onNodeWithText("Solo en este dispositivo").performScrollTo().assertIsDisplayed()
        assertEquals(listOf("toggle"), calls)
    }

    @Test
    fun `evento de todo el dia de varios dias`() {
        val event = anEvent(date = DAY, endDate = DAY.plusDays(2), startTime = LocalTime.MIDNIGHT, endTime = LocalTime.MIDNIGHT)

        assertEquals(EventWhen("Sáb 26 sept → lun 28 sept", "Todo el día · 3 días"), event.whenText())
    }

    @Test
    fun `evento que cruza la medianoche cuenta la duracion entre dias`() {
        val event = anEvent(date = DAY, endDate = DAY.plusDays(1), startTime = at("21:30"), endTime = at("01:00"))

        assertEquals(EventWhen("Sáb 26 sept → dom 27 sept", "21:30 – 01:00 · 3 h 30 min"), event.whenText())
    }

    @Test
    fun `duraciones`() {
        assertEquals("45 min", formatDuration(45))
        assertEquals("2 h", formatDuration(120))
        assertEquals("1 h 5 min", formatDuration(65))
    }
}
