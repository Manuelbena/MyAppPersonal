package com.syncro.presentation.calendar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.syncro.testutil.aTask
import com.syncro.testutil.anEvent
import com.syncro.testutil.at
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Etiquetas de la celda del día: una tarea siempre se ve como tarea, nunca como evento. */
@RunWith(RobolectricTestRunner::class)
class CalendarSnippetsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `una tarea sin hora se ve como tarea y no como evento de todo el dia`() {
        // Regresión: las tareas de Google (a las 00:00) se pintaban como eventos de todo el día
        compose.setContent { TaskSnippet(aTask(title = "Tomar creatina", time = at("00:00"))) }

        compose.onNodeWithContentDescription("Tarea").assertIsDisplayed()
    }

    @Test
    fun `una tarea completada se marca como completada`() {
        compose.setContent { TaskSnippet(aTask(title = "Tomar creatina", isCompleted = true)) }

        compose.onNodeWithContentDescription("Tarea completada").assertIsDisplayed()
    }

    @Test
    fun `un evento de todo el dia no lleva el check de tarea`() {
        compose.setContent { EventSnippet(anEvent(title = "Festivo", startTime = at("00:00"), endTime = at("00:00"))) }

        compose.onNodeWithContentDescription("Tarea").assertDoesNotExist()
    }
}
