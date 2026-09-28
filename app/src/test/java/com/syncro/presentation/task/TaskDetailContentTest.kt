package com.syncro.presentation.task

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.syncro.testutil.aTask
import com.syncro.testutil.at
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Detalle de una tarea: sus datos y el botón de completar/volver a pendiente. */
@RunWith(RobolectricTestRunner::class)
class TaskDetailContentTest {

    @get:Rule
    val compose = createComposeRule()

    private var toggles = 0

    @Test
    fun `muestra fecha, hora y descripcion y permite completarla`() {
        compose.setContent {
            TaskDetailContent(
                task = aTask(title = "Llamar al banco", description = "Preguntar por la tarjeta", time = at("09:30")),
                onToggleCompleted = { toggles++ }
            )
        }

        compose.onNodeWithText("Llamar al banco").assertIsDisplayed()
        compose.onNodeWithText("Sábado, 26 de septiembre").assertIsDisplayed()
        compose.onNodeWithText("09:30").assertIsDisplayed()
        compose.onNodeWithText("Preguntar por la tarjeta").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Completar tarea").performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun `una tarea completada de todo el dia`() {
        compose.setContent {
            TaskDetailContent(task = aTask(time = at("00:00"), isCompleted = true), onToggleCompleted = {})
        }

        compose.onNodeWithText("Todo el día").assertIsDisplayed()
        compose.onNodeWithText("Completada").assertIsDisplayed()
        compose.onNodeWithText("Marcar como pendiente").assertIsDisplayed()
    }
}
