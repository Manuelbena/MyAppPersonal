package com.syncro.presentation.assistant

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
import com.syncro.domain.model.ChatReply
import com.syncro.domain.model.DigestAnswer
import com.syncro.domain.model.assistantConversation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Chat del asistente: muestra la pregunta de los avisos, sus opciones llegan a quien lo muestra y
 * desaparecen al contestar, y vaciar el chat pide confirmación.
 */
@RunWith(RobolectricTestRunner::class)
class AssistantChatContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    @Test
    fun `pregunta por los avisos y al contestar se quitan las opciones`() {
        compose.setContent {
            AssistantChatContent(
                assistantConversation(null, notificationsAllowed = false),
                onReply = { calls += it.name },
                onClearChat = { calls += "clear" }
            )
        }

        compose.onNodeWithText("¿Te recuerdo tu día?", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Ahora no").assertIsDisplayed()
        compose.onNodeWithText("Sí, avísame").performClick()

        assertEquals(listOf(ChatReply.ENABLE_DIGEST.name), calls)
        compose.onNodeWithText("Ahora no").assertDoesNotExist()
    }

    // Regresión: la respuesta del usuario salía justo encima del botón con el mismo texto
    @Test
    fun `tras contestar solo se ve la burbuja del usuario, sin botones`() {
        compose.setContent {
            AssistantChatContent(
                assistantConversation(DigestAnswer.ACCEPTED, notificationsAllowed = true),
                onReply = { calls += it.name },
                onClearChat = { calls += "clear" }
            )
        }

        compose.onAllNodesWithText("Sí, avísame").assertCountEquals(1)
        compose.onNodeWithText("¡Hecho!", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Ahora no").assertDoesNotExist()
    }

    @Test
    fun `vaciar el chat pide confirmacion`() {
        compose.setContent {
            AssistantChatContent(
                assistantConversation(DigestAnswer.DECLINED, notificationsAllowed = false),
                onReply = { calls += it.name },
                onClearChat = { calls += "clear" }
            )
        }

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
        compose.setContent {
            AssistantChatContent(
                assistantConversation(DigestAnswer.DECLINED, false, allIds),
                onReply = { calls += it.name },
                onClearChat = { calls += "clear" }
            )
        }

        compose.onNodeWithText("No hay mensajes").assertIsDisplayed()
        compose.onNodeWithContentDescription("Vaciar el chat").assertDoesNotExist()
    }
}
