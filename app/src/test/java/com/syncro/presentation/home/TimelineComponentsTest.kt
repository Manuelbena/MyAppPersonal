package com.syncro.presentation.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onParent
import androidx.compose.ui.unit.dp
import com.syncro.domain.model.Subtask
import com.syncro.presentation.home.components.EventCard
import com.syncro.presentation.home.components.NowIndicator
import com.syncro.presentation.home.components.QuickTaskSheet
import com.syncro.presentation.home.components.TaskRow
import com.syncro.testutil.DAY
import com.syncro.testutil.aTask
import com.syncro.testutil.anEvent
import com.syncro.testutil.at
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalTime

/** Cómo se ven las horas en el timeline y el formulario de tarea rápida. */
@RunWith(RobolectricTestRunner::class)
class TimelineComponentsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `una tarea de todo el dia muestra Todo el dia en lugar de 00-00`() {
        compose.setContent { TaskRow(task = aTask(title = "Tomar creatina", time = at("00:00")), onToggle = {}, onClick = {}) }

        compose.onNodeWithText("Todo el").assertIsDisplayed()
        compose.onNodeWithText("00:00").assertDoesNotExist()
    }

    @Test
    fun `una tarea con hora muestra su hora`() {
        compose.setContent { TaskRow(task = aTask(title = "Gimnasio", time = at("18:30")), onToggle = {}, onClick = {}) }

        compose.onNodeWithText("18:30").assertIsDisplayed()
    }

    @Test
    fun `un evento que termina al dia siguiente lo indica debajo de la hora`() {
        compose.setContent {
            EventCard(
                event = anEvent(title = "Cena", date = DAY, endDate = DAY.plusDays(1), startTime = at("21:30"), endTime = at("01:00")),
                onSubtaskToggle = {}
            )
        }

        compose.onNodeWithText("01:00").assertIsDisplayed()
        compose.onNodeWithText("+1 día").assertIsDisplayed()
    }

    @Test
    fun `la tarjeta de evento resume las subtareas y se pueden marcar sin abrir el detalle`() {
        var opened = false
        val toggled = mutableListOf<String>()
        compose.setContent {
            EventCard(
                event = anEvent(
                    title = "Mudanza",
                    subtasks = listOf(Subtask("Cajas", isCompleted = true), Subtask("Furgoneta", isCompleted = false))
                ),
                onSubtaskToggle = { toggled += it },
                onClick = { opened = true }
            )
        }

        compose.onNodeWithText("1/2").assertIsDisplayed()
        compose.onNodeWithText("Furgoneta").assertDoesNotExist()

        compose.onNodeWithText("1/2").performClick()
        compose.onNodeWithText("Furgoneta").performClick()
        assertEquals(listOf("Furgoneta"), toggled)
        assertEquals(false, opened)
    }

    // Material pide al menos 48 dp para todo lo que se pulsa; el círculo se ve de 26 dp
    @Test
    fun `completar y las subtareas tienen el area tactil minima de Material`() {
        compose.setContent {
            Column {
                EventCard(event = anEvent(subtasks = listOf(Subtask("Cajas", isCompleted = false))), onSubtaskToggle = {})
                TaskRow(task = aTask(), onToggle = {}, onClick = {})
            }
        }

        compose.onNodeWithContentDescription("Completar evento").assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp)
        compose.onNodeWithContentDescription("Completar").assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp)
        compose.onNodeWithContentDescription("Ver subtareas").performClick()
        compose.onNodeWithText("Cajas").onParent().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `un evento en curso dice cuanto le queda`() {
        compose.setContent {
            EventCard(
                event = anEvent(title = "Comida", startTime = at("12:00"), endTime = at("13:30")),
                onSubtaskToggle = {},
                now = DAY.atTime(13, 5)
            )
        }

        compose.onNodeWithText("En curso · quedan 25 min").assertIsDisplayed()
    }

    @Test
    fun `la linea Ahora muestra la hora actual`() {
        compose.setContent { NowIndicator(now = DAY.atTime(12, 25)) }

        compose.onNodeWithContentDescription("Ahora, 12:25").assertIsDisplayed()
    }

    @Test
    fun `tocar la tarjeta de evento abre el detalle`() {
        var opened = false
        compose.setContent {
            EventCard(event = anEvent(title = "Mudanza", description = null), onSubtaskToggle = {}, onClick = { opened = true })
        }

        compose.onNodeWithText("Mudanza").performClick()
        assertEquals(true, opened)
    }

    @Test
    fun `en tarea rapida el chip Todo el dia guarda la tarea a las 00-00`() {
        var savedTime: LocalTime? = null
        compose.setContent { QuickTaskSheet(onDismiss = {}, onSave = { _, _, _, time -> savedTime = time }) }

        // El título es el único campo editable visible (la descripción se abre con "Detalles")
        compose.onNode(hasSetTextAction()).performTextInput("Tomar creatina")
        compose.onNodeWithText("Todo el día").performClick()
        compose.onNodeWithText("Guardar").performClick()

        assertEquals(LocalTime.MIDNIGHT, savedTime)
    }
}
