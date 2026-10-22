package com.syncro.presentation.assistant

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.syncro.domain.model.AppUpdate
import com.syncro.domain.model.AssistantConversation
import com.syncro.domain.model.DigestAnswer
import com.syncro.domain.model.FocusCandidates
import com.syncro.domain.model.LeftoverChoice
import com.syncro.domain.model.LeftoverOutcome
import com.syncro.domain.model.LeftoverTasks
import com.syncro.domain.model.MoveTarget
import com.syncro.domain.model.Payday
import com.syncro.domain.model.assistantConversation
import com.syncro.testutil.DAY
import com.syncro.testutil.aTask
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Chat del asistente: las opciones de cada pregunta llegan a quien lo muestra y desaparecen al
 * contestar, las tareas pendientes se ven con sus acciones en el modo "una a una" y vaciar el chat
 * pide confirmación.
 */
@RunWith(RobolectricTestRunner::class)
class AssistantChatContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(conversation: AssistantConversation) {
        compose.setContent {
            AssistantChatContent(
                conversation = conversation,
                firstSelectableDay = DAY.plusDays(1),
                onReply = { reply, selected, _ -> calls += if (selected.isEmpty()) reply.name else "${reply.name}$selected" },
                onTaskAction = { taskId, action, _ -> calls += "$action($taskId)" },
                onClearChat = { calls += "clear" }
            )
        }
    }

    private val leftovers = LeftoverTasks(DAY, MoveTarget.TOMORROW, listOf(aTask(id = "t1", title = "Llamar al banco")))

    @Test
    fun `mientras se arma la conversacion se ve el esqueleto y no No hay mensajes`() {
        compose.setContent {
            AssistantChatContent(conversation = null, firstSelectableDay = DAY, onReply = { _, _, _ -> }, onTaskAction = { _, _, _ -> }, onClearChat = {})
        }

        compose.onNodeWithContentDescription("Cargando").assertIsDisplayed()
        compose.onNodeWithText("No hay mensajes").assertDoesNotExist()
    }

    @Test
    fun `la cabecera lleva la foto del asistente`() {
        show(assistantConversation(null, notificationsAllowed = false))

        compose.onNodeWithContentDescription("Foto del asistente").assertIsDisplayed()
        compose.onNodeWithContentDescription("En línea").assertIsDisplayed()
        compose.onNodeWithText("Asistente").assertIsDisplayed()
    }

    @Test
    fun `pregunta por los avisos y al contestar se quitan las opciones`() {
        show(assistantConversation(null, notificationsAllowed = false))

        compose.onNodeWithText("¿Te recuerdo tu día?", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Ahora no").assertIsDisplayed()
        compose.onNodeWithText("Sí, avísame").performClick()

        assertEquals(listOf("ENABLE_DIGEST"), calls)
        compose.onNodeWithText("Ahora no").assertDoesNotExist()
    }

    // Regresión: la respuesta del usuario salía justo encima del botón con el mismo texto
    @Test
    fun `tras contestar solo se ve la burbuja del usuario, sin botones`() {
        show(assistantConversation(DigestAnswer.ACCEPTED, notificationsAllowed = true))

        compose.onAllNodesWithText("Sí, avísame").assertCountEquals(1)
        compose.onNodeWithText("¡Hecho!", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Ahora no").assertDoesNotExist()
    }

    @Test
    fun `el dia de nomina ofrece apuntarla o dejarlo para luego`() {
        show(assistantConversation(DigestAnswer.ACCEPTED, true, payday = Payday(DAY, salaryCents = null, answer = null)))

        compose.onNodeWithText("Ahora no").assertExists()
        compose.onNodeWithText("Apuntar nómina").performScrollTo().performClick()

        assertEquals(listOf("PAYDAY_REGISTER"), calls)
    }

    @Test
    fun `las pendientes se ven con sus tres opciones`() {
        show(assistantConversation(DigestAnswer.ACCEPTED, true, leftovers))

        compose.onNodeWithText("Llamar al banco").assertIsDisplayed()
        compose.onNodeWithText("Déjalas").assertIsDisplayed()
        compose.onNodeWithText("Elegir una a una").assertIsDisplayed()
        compose.onNodeWithText("Todas a mañana").performClick()

        assertEquals(listOf("LEFTOVERS_MOVE_ALL"), calls)
        compose.onNodeWithText("Déjalas").assertDoesNotExist()
    }

    @Test
    fun `una a una cada tarea tiene sus acciones`() {
        val outcome = LeftoverOutcome(DAY, LeftoverChoice.ONE_BY_ONE, MoveTarget.TOMORROW, total = 1)
        show(assistantConversation(DigestAnswer.ACCEPTED, true, leftovers, listOf(outcome)))

        compose.onNodeWithText("Otro día").assertIsDisplayed()
        compose.onNodeWithText("Mañana").performClick()
        compose.onNodeWithText("Hecha").performClick()

        assertEquals(listOf("MOVE_TO_TARGET(t1)", "DONE(t1)"), calls)
    }

    @Test
    fun `se eligen hasta 3 prioridades y Listo manda las marcadas en el orden de la lista`() {
        val candidates = FocusCandidates(DAY, (1..4).map { aTask(id = "t$it", title = "Tarea $it") })
        show(assistantConversation(DigestAnswer.ACCEPTED, true, focus = candidates))

        compose.onNodeWithText("Listo").assertIsNotEnabled()
        listOf("Tarea 3", "Tarea 1", "Tarea 2").forEach { compose.onNodeWithText(it).performClick() }
        compose.onNodeWithText("3 de 3 elegidas").assertExists()
        // Cupo lleno: la cuarta no se puede marcar
        compose.onNodeWithText("Tarea 4").assertIsNotEnabled().performClick()
        compose.onNodeWithText("Listo").performScrollTo().performClick()

        assertEquals(listOf("FOCUS_CONFIRM[t1, t2, t3]"), calls)
    }

    @Test
    fun `vaciar el chat pide confirmacion`() {
        show(assistantConversation(DigestAnswer.DECLINED, notificationsAllowed = false))

        compose.onNodeWithContentDescription("Vaciar el chat").performClick()
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(emptyList<String>(), calls)

        compose.onNodeWithContentDescription("Vaciar el chat").performClick()
        compose.onNodeWithText("Vaciar").performClick()
        assertEquals(listOf("clear"), calls)
    }

    @Test
    fun `con el chat vacio no se ofrece vaciarlo`() {
        val allIds = assistantConversation(DigestAnswer.DECLINED, false).messages.map { it.id }.toSet()
        show(assistantConversation(DigestAnswer.DECLINED, false, clearedIds = allIds))

        compose.onNodeWithText("No hay mensajes").assertIsDisplayed()
        compose.onNodeWithContentDescription("Vaciar el chat").assertDoesNotExist()
    }

    // Regresión: al tocar "Actualizar" se ocultaban los botones y, al volver de la tienda sin
    // actualizar, ya no había forma de abrirla otra vez
    @Test
    fun `Actualizar abre la tienda y deja los botones por si vuelve sin actualizar`() {
        show(assistantConversation(DigestAnswer.ACCEPTED, notificationsAllowed = true, update = AppUpdate(42)))

        compose.onNodeWithText("Actualizar").performScrollTo().performClick()

        assertEquals(listOf("UPDATE_NOW"), calls)
        compose.onNodeWithText("Actualizar").assertExists()
        compose.onNodeWithText("Más tarde").assertExists()
    }

    @Test
    fun `cada dia lleva su chip como en WhatsApp`() {
        val messages = assistantConversation(DigestAnswer.ACCEPTED, notificationsAllowed = true).messages
        val days = listOf(DAY.minusDays(1)) + List(messages.size - 1) { DAY }
        compose.setContent {
            AssistantChatContent(
                conversation = AssistantConversation(messages.zip(days) { message, day -> message.copy(day = day) }),
                today = DAY,
                firstSelectableDay = DAY,
                onReply = { _, _, _ -> },
                onTaskAction = { _, _, _ -> },
                onClearChat = {}
            )
        }

        compose.onAllNodesWithText("Hoy").assertCountEquals(1)
        // Al abrir baja al final: el de ayer queda arriba
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("Ayer").assertExists()
    }

    @Test
    fun `etiquetas de dia del chat`() {
        assertEquals("Hoy", chatDayLabel(DAY, DAY))
        assertEquals("Ayer", chatDayLabel(DAY.minusDays(1), DAY))
        // DAY es sábado 26 de septiembre de 2026
        assertEquals("Martes", chatDayLabel(DAY.minusDays(4), DAY))
        assertEquals("12 de septiembre", chatDayLabel(DAY.minusDays(14), DAY))
        assertEquals("26 de septiembre de 2025", chatDayLabel(DAY.minusYears(1), DAY))
    }
}
