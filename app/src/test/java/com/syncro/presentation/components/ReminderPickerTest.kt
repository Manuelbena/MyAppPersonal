package com.syncro.presentation.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.syncro.presentation.event.EventDetailContent
import com.syncro.presentation.task.TaskDetailContent
import com.syncro.testutil.DAY
import com.syncro.testutil.aTask
import com.syncro.testutil.anEvent
import com.syncro.testutil.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * El aviso en pantalla: los tramos del formulario (distintos con hora y en todo el día), qué se
 * dice debajo y cómo se ve en el detalle.
 */
@RunWith(RobolectricTestRunner::class)
class ReminderPickerTest {

    @get:Rule
    val compose = createComposeRule()

    private var minutes by mutableStateOf<Int?>(null)

    // DAY es el sábado 26 de septiembre; "ahora" es el día antes por la mañana
    private val now = DAY.minusDays(1).atTime(10, 0)

    private fun showPicker(isAllDay: Boolean = false) {
        compose.setContent {
            ReminderPicker(
                reminderMinutes = minutes,
                start = if (isAllDay) DAY.atStartOfDay() else DAY.atTime(19, 0),
                isAllDay = isAllDay,
                accent = Color.Blue,
                onChange = { minutes = it },
                now = now
            )
        }
    }

    @Test
    fun `por defecto sin aviso`() {
        showPicker()

        compose.onNodeWithText("Sin aviso").assertIsSelected()
        compose.onNodeWithText("No te avisaremos").assertIsDisplayed()
    }

    @Test
    fun `elegir 15 min dice cuando sonara y que avisa Syncro, no Google`() {
        showPicker()

        compose.onNodeWithText("15 min").performClick()

        assertEquals(15, minutes)
        compose.onNodeWithText("Te avisaremos mañana a las 18:45").assertIsDisplayed()
        compose.onNodeWithText("Te avisa Syncro en este móvil (Google no te enviará otro aviso)").assertIsDisplayed()
    }

    @Test
    fun `en todo el dia los tramos son horas del dia`() {
        showPicker(isAllDay = true)

        compose.onNodeWithText("El día antes a las 20:00").performClick()

        assertEquals(4 * 60, minutes)
        compose.onNodeWithText("Te avisaremos hoy a las 20:00").assertIsDisplayed()
    }

    @Test
    fun `una hora elegida a mano marca Otra hora`() {
        minutes = 90
        showPicker()

        compose.onNodeWithText("Otra hora…").assertIsSelected()
        compose.onNodeWithText("Te avisaremos mañana a las 17:30").assertIsDisplayed()
    }

    @Test
    fun `una hora ya pasada avisa de que no sonara`() {
        minutes = 3 * 24 * 60
        showPicker()

        compose.onNodeWithText("Esa hora ya ha pasado: no te llegará el aviso").assertIsDisplayed()
    }

    @Test
    fun `quitar el aviso`() {
        minutes = 15
        showPicker()

        compose.onNodeWithText("Sin aviso").performClick()

        assertNull(minutes)
    }

    @Test
    fun `el detalle de un evento dice su aviso`() {
        compose.setContent {
            EventDetailContent(
                event = anEvent(date = DAY, startTime = at("19:00")).copy(reminderMinutes = 15),
                onToggleCompleted = {}, onSubtaskToggle = {}, onEdit = {}, onShare = {}, onDelete = {}
            )
        }

        compose.onNodeWithText("AVISO").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("15 min antes").assertIsDisplayed()
    }

    @Test
    fun `el detalle de una tarea dice su aviso`() {
        compose.setContent {
            TaskDetailContent(task = aTask(date = DAY, time = at("00:00")).copy(reminderMinutes = -540), onToggleCompleted = {}, onDelete = {})
        }

        compose.onNodeWithText("Ese día a las 9:00").performScrollTo().assertIsDisplayed()
    }
}
