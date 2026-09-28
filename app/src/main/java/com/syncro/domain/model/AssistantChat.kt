package com.syncro.domain.model

/** Lo que el usuario contestó a "¿Te recuerdo tu día?". */
enum class DigestAnswer { ACCEPTED, DECLINED }

/**
 * Un mensaje del chat del asistente. El [id] es estable (no depende del orden) para poder
 * recordar cuáles ha leído el usuario.
 */
data class ChatMessage(val id: String, val fromAssistant: Boolean, val text: String)

/** Opciones que el usuario puede tocar bajo la pregunta; desaparecen en cuanto contesta. */
enum class ChatReply(val label: String) {
    ENABLE_DIGEST("Sí, avísame"),
    NOT_NOW("Ahora no")
}

data class AssistantConversation(val messages: List<ChatMessage>, val replies: List<ChatReply>) {
    /** Mensajes del asistente que el usuario aún no ha visto (el número del icono de Asistente). */
    fun unreadCount(readIds: Set<String>): Int = messages.count { it.fromAssistant && it.id !in readIds }
}

/**
 * Construye la conversación a partir de lo que se sabe (no es un historial guardado): así siempre
 * refleja el estado real, p. ej. si el usuario activa las notificaciones desde los ajustes.
 *
 * @param answer lo que contestó el usuario, o null si aún no ha contestado
 * @param notificationsAllowed si Android deja publicar los avisos ahora mismo
 * @param clearedIds mensajes que el usuario borró al vaciar el chat (no vuelven a salir)
 */
fun assistantConversation(
    answer: DigestAnswer?,
    notificationsAllowed: Boolean,
    clearedIds: Set<String> = emptySet()
): AssistantConversation {
    val messages = mutableListOf(assistant(ID_HELLO, "¡Hola! Soy tu asistente. Por aquí te iré contando cosas de tu día."))

    when {
        // Ya tiene los avisos y nunca se le preguntó (p. ej. Android 12, sin permiso que pedir): se le cuenta y ya
        answer == null && notificationsAllowed ->
            messages += assistant(ID_DIGEST_ON, "Cada mañana a las 9:00 te cuento lo que te espera, y a las 21:00 cómo ha ido el día. 🔔")
        else -> {
            messages += assistant(
                ID_DIGEST_QUESTION,
                "¿Te recuerdo tu día? Cada mañana a las 9:00 te cuento lo que te espera, y a las 21:00 " +
                    "cómo ha ido el día. Funciona también sin internet."
            )
            when (answer) {
                null -> Unit
                DigestAnswer.ACCEPTED -> {
                    messages += user(ID_USER_ANSWER, ChatReply.ENABLE_DIGEST.label)
                    messages += if (notificationsAllowed) {
                        assistant(ID_DIGEST_ACCEPTED, "¡Hecho! Te escribo mañana a las 9:00. 😊")
                    } else {
                        // Aceptó pero Android no lo permite (denegó el diálogo o las tiene silenciadas)
                        assistant(
                            ID_DIGEST_BLOCKED,
                            "Android no me deja enviarte avisos. Puedes activarlos en los ajustes de notificaciones de Syncro."
                        )
                    }
                }
                DigestAnswer.DECLINED -> {
                    messages += user(ID_USER_ANSWER, ChatReply.NOT_NOW.label)
                    messages += assistant(
                        ID_DIGEST_DECLINED,
                        "Vale, sin problema. Si cambias de idea, puedes activarlos en los ajustes de notificaciones de Syncro."
                    )
                }
            }
        }
    }

    val visible = messages.filter { it.id !in clearedIds }
    // Las opciones solo mientras la pregunta está sin contestar (y a la vista)
    val replies = if (answer == null && visible.any { it.id == ID_DIGEST_QUESTION }) {
        listOf(ChatReply.ENABLE_DIGEST, ChatReply.NOT_NOW)
    } else {
        emptyList()
    }
    return AssistantConversation(visible, replies)
}

private fun assistant(id: String, text: String) = ChatMessage(id, fromAssistant = true, text = text)
private fun user(id: String, text: String) = ChatMessage(id, fromAssistant = false, text = text)

private const val ID_HELLO = "hello"
private const val ID_DIGEST_ON = "digest_on"
private const val ID_DIGEST_QUESTION = "digest_question"
private const val ID_USER_ANSWER = "user_digest_answer"
private const val ID_DIGEST_ACCEPTED = "digest_accepted"
private const val ID_DIGEST_BLOCKED = "digest_blocked"
private const val ID_DIGEST_DECLINED = "digest_declined"
