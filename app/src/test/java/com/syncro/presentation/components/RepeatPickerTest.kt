package com.syncro.presentation.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.syncro.domain.model.Recurrence
import com.syncro.domain.model.RepeatFrequency
import com.syncro.domain.model.RepeatScope
import com.syncro.domain.model.SyncroItem
import com.syncro.presentation.event.AddEventContent
import com.syncro.presentation.task.TaskDetailContent
import com.syncro.testutil.DAY
import com.syncro.testutil.aTask
import com.syncro.testutil.anEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

/**
 * Repetir tareas y eventos en pantalla: el selector (L M X J V S D), el texto de la regla y las
 * preguntas de "solo esta / esta y las siguientes" al editar y al borrar.
 */
@RunWith(RobolectricTestRunner::class)
class RepeatPickerTest {

    @get:Rule
    val compose = createComposeRule()

    // region Texto de la regla

    @Test
    fun `la regla se explica en palabras`() {
        val start = LocalDate.of(2026, 9, 27)

        assertEquals("Cada día", Recurrence(RepeatFrequency.DAILY).describe(start))
        assertEquals("Cada semana: lunes y jueves", Recurrence(RepeatFrequency.WEEKLY, setOf(THURSDAY, MONDAY)).describe(start))
        assertEquals("De lunes a viernes", Recurrence(RepeatFrequency.WEEKLY, setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)).describe(start))
        assertEquals("Cada mes, el día 27", Recurrence(RepeatFrequency.MONTHLY).describe(start))
        assertEquals("Cada mes, el día 31 (o el último del mes)", Recurrence(RepeatFrequency.MONTHLY).describe(LocalDate.of(2026, 1, 31)))
        assertEquals("Cada año, el 27 de septiembre", Recurrence(RepeatFrequency.YEARLY).describe(start))
    }

    // endregion

    // region Selector

    private var repeat by mutableStateOf<Recurrence?>(null)

    private fun showPicker() {
        compose.setContent {
            RepeatPicker(repeat = repeat, startDate = DAY, accent = Color.Blue, onChange = { repeat = it })
        }
    }

    @Test
    fun `por defecto no se repite`() {
        showPicker()

        compose.onNodeWithText("No se repite").assertIsDisplayed()
    }

    @Test
    fun `cada semana muestra los dias de lunes a domingo con el del dia elegido marcado`() {
        showPicker()

        compose.onNodeWithText("Cada semana").performClick()

        listOf("L", "M", "X", "J", "V", "S", "D").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        // DAY es sábado
        compose.onNodeWithContentDescription("sábado").assertIsSelected()
        compose.onNodeWithContentDescription("lunes").assertIsNotSelected()
        assertEquals(setOf(SATURDAY), repeat?.weekdays)
    }

    @Test
    fun `se pueden marcar varios dias pero nunca quedarse sin ninguno`() {
        showPicker()
        compose.onNodeWithText("Cada semana").performClick()

        compose.onNodeWithContentDescription("lunes").performClick()
        compose.onNodeWithContentDescription("sábado").performClick()
        // Quitar el último que queda no hace nada
        compose.onNodeWithContentDescription("lunes").performClick()

        assertEquals(setOf(MONDAY), repeat?.weekdays)
        compose.onNodeWithText("Cada semana: lunes").assertIsDisplayed()
    }

    @Test
    fun `volver a No quita la repeticion`() {
        repeat = Recurrence(RepeatFrequency.MONTHLY)
        showPicker()

        compose.onNodeWithText("No").performClick()

        assertNull(repeat)
    }

    // endregion

    // region Editar y borrar repeticiones

    private var savedScope: RepeatScope? = null
    private var savedRepeat: Recurrence? = null

    private fun showEventForm(event: SyncroItem.Event) {
        compose.setContent {
            AddEventContent(
                eventToEdit = event,
                onDismiss = {},
                onSave = { _, _, _, _, _, _, _, _, _, _, _, repeat, scope ->
                    savedRepeat = repeat
                    savedScope = scope
                }
            )
        }
    }

    private val weeklyEvent = anEvent(title = "Gimnasio").copy(seriesId = "s", repeat = Recurrence.weeklyOn(DAY))

    @Test
    fun `al guardar una repeticion se pregunta si es solo este o tambien los siguientes`() {
        showEventForm(weeklyEvent)

        compose.onNodeWithText("Guardar").performClick()
        compose.onNodeWithText("Este y los siguientes").performClick()

        assertEquals(RepeatScope.THIS_AND_FOLLOWING, savedScope)
        assertEquals(weeklyEvent.repeat, savedRepeat)
    }

    @Test
    fun `cambiar la regla de una repeticion vale para las siguientes sin preguntar`() {
        showEventForm(weeklyEvent)

        compose.onNodeWithText("Cada mes").performScrollTo().performClick()
        compose.onNodeWithText("Guardar").performClick()

        assertEquals(RepeatScope.THIS_AND_FOLLOWING, savedScope)
        assertEquals(Recurrence(RepeatFrequency.MONTHLY), savedRepeat)
    }

    @Test
    fun `un evento suelto se guarda sin preguntar`() {
        showEventForm(anEvent(title = "Dentista"))

        compose.onNodeWithText("Guardar").performClick()

        assertEquals(RepeatScope.THIS, savedScope)
    }

    @Test
    fun `una tarea que se repite lo dice y al borrarla pregunta si tambien las siguientes`() {
        var deleted: RepeatScope? = null
        compose.setContent {
            TaskDetailContent(
                task = aTask(title = "Regar").copy(seriesId = "s", repeat = Recurrence(RepeatFrequency.DAILY)),
                onToggleCompleted = {},
                onDelete = { deleted = RepeatScope.THIS },
                onDeleteFollowing = { deleted = RepeatScope.THIS_AND_FOLLOWING }
            )
        }

        compose.onNodeWithText("SE REPITE").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Eliminar tarea").performScrollTo().performClick()
        compose.onNodeWithText("Solo esta").assertIsDisplayed()
        compose.onNodeWithText("Esta y las siguientes").performClick()

        assertEquals(RepeatScope.THIS_AND_FOLLOWING, deleted)
    }

    // endregion
}
