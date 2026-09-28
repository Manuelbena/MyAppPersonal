package com.syncro.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Conversación del asistente sobre los avisos diarios.
 * Responsabilidades: preguntar "¿Te recuerdo tu día?" solo si hace falta, reflejar la respuesta del
 * usuario y la contestación del asistente, ofrecer las respuestas rápidas que tocan en cada caso y
 * contar los mensajes sin leer. Riesgos: preguntar a quien ya tiene los avisos, dejar al usuario sin
 * salida si Android bloquea el permiso, y contar como "sin leer" los mensajes del propio usuario.
 */
class AssistantChatTest {

    private fun AssistantConversation.texts() = messages.map { it.text }

    @Test
    fun `sin contestar y sin permiso pregunta y ofrece si y ahora no`() {
        val chat = assistantConversation(answer = null, notificationsAllowed = false)

        assertTrue(chat.messages.all { it.fromAssistant })
        assertTrue(chat.messages.last().text.startsWith("¿Te recuerdo tu día?"))
        assertEquals(listOf(ChatReply.ENABLE_DIGEST, ChatReply.NOT_NOW), chat.replies)
    }

    @Test
    fun `sin contestar pero con avisos permitidos no pregunta nada`() {
        val chat = assistantConversation(answer = null, notificationsAllowed = true)

        assertFalse(chat.texts().any { it.startsWith("¿Te recuerdo") })
        assertTrue(chat.messages.last().text.contains("9:00"))
        assertTrue(chat.replies.isEmpty())
    }

    @Test
    fun `al aceptar con permiso aparece la respuesta del usuario y la confirmacion`() {
        val chat = assistantConversation(DigestAnswer.ACCEPTED, notificationsAllowed = true)

        val answer = chat.messages[chat.messages.size - 2]
        assertFalse(answer.fromAssistant)
        assertEquals("Sí, avísame", answer.text)
        assertTrue(chat.messages.last().text.startsWith("¡Hecho!"))
        assertTrue(chat.replies.isEmpty())
    }

    @Test
    fun `al aceptar pero con Android bloqueando explica como activarlos y no deja opciones`() {
        val chat = assistantConversation(DigestAnswer.ACCEPTED, notificationsAllowed = false)

        assertTrue(chat.messages.last().text.contains("ajustes"))
        assertTrue(chat.replies.isEmpty())
    }

    @Test
    fun `al decir ahora no se quitan las opciones`() {
        val chat = assistantConversation(DigestAnswer.DECLINED, notificationsAllowed = false)

        assertEquals("Ahora no", chat.messages[chat.messages.size - 2].text)
        assertTrue(chat.messages.last().text.startsWith("Vale"))
        assertTrue(chat.replies.isEmpty())
    }

    @Test
    fun `vaciar el chat oculta esos mensajes pero no los que lleguen despues`() {
        val before = assistantConversation(DigestAnswer.ACCEPTED, notificationsAllowed = true)
        val cleared = before.messages.map { it.id }.toSet()

        assertTrue(assistantConversation(DigestAnswer.ACCEPTED, true, cleared).messages.isEmpty())
        // Más tarde Android bloquea los avisos: ese mensaje es nuevo y sí aparece
        val later = assistantConversation(DigestAnswer.ACCEPTED, false, cleared)
        assertEquals(1, later.messages.size)
        assertTrue(later.messages.single().text.contains("ajustes"))
    }

    @Test
    fun `si se vacia sin contestar desaparecen tambien las opciones`() {
        val cleared = assistantConversation(null, false).messages.map { it.id }.toSet()

        val chat = assistantConversation(null, false, cleared)
        assertTrue(chat.messages.isEmpty())
        assertTrue(chat.replies.isEmpty())
    }

    @Test
    fun `los ids son estables y unicos`() {
        val cases = listOf(null, DigestAnswer.ACCEPTED, DigestAnswer.DECLINED).flatMap { answer ->
            listOf(true, false).map { assistantConversation(answer, it) }
        }
        cases.forEach { chat -> assertEquals(chat.messages.size, chat.messages.map { it.id }.toSet().size) }
        // La pregunta es el mismo mensaje antes y después de contestar (no vuelve a contar como nuevo)
        val before = assistantConversation(null, false).messages.last().id
        assertTrue(assistantConversation(DigestAnswer.ACCEPTED, true).messages.any { it.id == before })
    }

    @Test
    fun `sin leer cuenta solo mensajes del asistente no vistos`() {
        val chat = assistantConversation(DigestAnswer.DECLINED, notificationsAllowed = false)
        val assistantIds = chat.messages.filter { it.fromAssistant }.map { it.id }

        assertEquals(assistantIds.size, chat.unreadCount(emptySet()))
        // La respuesta del usuario no cuenta aunque no esté marcada
        assertEquals(0, chat.unreadCount(assistantIds.toSet()))
        // Solo queda la contestación nueva del asistente
        assertEquals(1, chat.unreadCount(assistantIds.dropLast(1).toSet()))
    }
}
