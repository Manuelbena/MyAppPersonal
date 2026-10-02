package com.syncro.domain.model

import com.syncro.testutil.DAY
import com.syncro.testutil.aTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Conversación del asistente.
 * Responsabilidades: preguntar "¿Te recuerdo tu día?" solo si hace falta; preguntar qué hacer con
 * las tareas pendientes (todas / una a una / déjalas) y reflejar lo decidido; que cada pregunta
 * lleve sus opciones y las pierda al contestar; contar los mensajes sin leer. Riesgos: preguntar a
 * quien ya tiene los avisos, que un repaso contestado vuelva a preguntar, perder la lista en el
 * modo "una a una", contar como "sin leer" los mensajes del propio usuario.
 */
class AssistantChatTest {

    private fun AssistantConversation.byId(id: String) = messages.firstOrNull { it.id == id }
    private val AssistantConversation.optionMessages get() = messages.filter { it.options.isNotEmpty() }

    // region Avisos diarios

    @Test
    fun `sin contestar y sin permiso pregunta y ofrece si y ahora no`() {
        val chat = assistantConversation(answer = null, notificationsAllowed = false)

        val question = chat.messages.last()
        assertTrue(question.text.startsWith("¿Te recuerdo tu día?"))
        assertEquals(listOf(ChatReply.ENABLE_DIGEST, ChatReply.NOT_NOW), question.options.map { it.reply })
    }

    @Test
    fun `sin contestar pero con avisos permitidos no pregunta nada`() {
        val chat = assistantConversation(answer = null, notificationsAllowed = true)

        assertFalse(chat.messages.any { it.text.startsWith("¿Te recuerdo") })
        assertTrue(chat.optionMessages.isEmpty())
    }

    @Test
    fun `al aceptar con permiso aparece la respuesta del usuario y la confirmacion, sin opciones`() {
        val chat = assistantConversation(DigestAnswer.ACCEPTED, notificationsAllowed = true)

        val answer = chat.messages[chat.messages.size - 2]
        assertFalse(answer.fromAssistant)
        assertEquals("Sí, avísame", answer.text)
        assertTrue(chat.messages.last().text.startsWith("¡Hecho!"))
        assertTrue(chat.optionMessages.isEmpty())
    }

    @Test
    fun `al aceptar pero con Android bloqueando explica como activarlos`() {
        val chat = assistantConversation(DigestAnswer.ACCEPTED, notificationsAllowed = false)

        assertTrue(chat.messages.last().text.contains("ajustes"))
        assertTrue(chat.optionMessages.isEmpty())
    }

    @Test
    fun `al decir ahora no se quitan las opciones`() {
        val chat = assistantConversation(DigestAnswer.DECLINED, notificationsAllowed = false)

        assertEquals("Ahora no", chat.messages[chat.messages.size - 2].text)
        assertTrue(chat.messages.last().text.startsWith("Vale"))
        assertTrue(chat.optionMessages.isEmpty())
    }

    // endregion

    // region Tareas pendientes

    private val evening = LeftoverTasks(
        reviewDate = DAY,
        target = MoveTarget.TOMORROW,
        tasks = listOf(aTask(id = "t1", title = "Llamar al banco"), aTask(id = "t2", title = "Comprar regalo"))
    )

    private fun withLeftovers(leftovers: LeftoverTasks?, vararg outcomes: LeftoverOutcome) =
        assistantConversation(DigestAnswer.ACCEPTED, notificationsAllowed = true, leftovers = leftovers, outcomes = outcomes.toList())

    @Test
    fun `con pendientes sin contestar pregunta con la lista y las tres opciones`() {
        val question = withLeftovers(evening).messages.last()

        assertEquals("Se te quedaron 2 tareas sin hacer. ¿Qué hacemos?", question.text)
        assertEquals(listOf("Llamar al banco", "Comprar regalo"), question.tasks.map { it.title })
        assertNull("La lista de la pregunta es informativa", question.taskTarget)
        assertEquals(
            listOf("Todas a mañana", "Elegir una a una", "Déjalas"),
            question.options.map { it.label }
        )
    }

    @Test
    fun `por la manana saluda y propone pasarlas a hoy`() {
        val morning = evening.copy(reviewDate = DAY.minusDays(1), target = MoveTarget.TODAY)

        val question = withLeftovers(morning).messages.last()

        assertTrue(question.text.startsWith("¡Buenos días!"))
        assertEquals("Todas a hoy", question.options.first().label)
    }

    @Test
    fun `sin pendientes no hay pregunta`() {
        val chat = withLeftovers(evening.copy(tasks = emptyList()))

        assertTrue(chat.messages.none { it.id.startsWith("leftovers-") })
    }

    @Test
    fun `al pasarlas todas se ve la respuesta y la confirmacion, sin opciones`() {
        val outcome = LeftoverOutcome(DAY, LeftoverChoice.MOVE_ALL, MoveTarget.TOMORROW, total = 2, moved = 2)

        // Ya movidas: el repaso de hoy no tiene pendientes, pero la conversación se mantiene
        val chat = withLeftovers(evening.copy(tasks = emptyList()), outcome)

        assertEquals("Todas a mañana", chat.byId("leftovers-$DAY-answer")!!.text)
        assertEquals("Hecho ✅ He pasado 2 tareas a mañana.", chat.byId("leftovers-$DAY-done")!!.text)
        assertTrue(chat.optionMessages.isEmpty())
        assertTrue("La pregunta ya no lleva la lista", chat.byId("leftovers-$DAY")!!.tasks.isEmpty())
    }

    @Test
    fun `un repaso contestado no vuelve a preguntar aunque sigan pendientes`() {
        val kept = LeftoverOutcome(DAY, LeftoverChoice.KEEP, MoveTarget.TOMORROW, total = 2)

        val chat = withLeftovers(evening, kept)

        assertTrue(chat.optionMessages.isEmpty())
        assertEquals(1, chat.messages.count { it.id == "leftovers-$DAY" })
        assertTrue(chat.byId("leftovers-$DAY-done")!!.text.startsWith("Vale, las dejo"))
    }

    @Test
    fun `una a una muestra la lista con acciones hasta resolverlas todas`() {
        val inProgress = LeftoverOutcome(DAY, LeftoverChoice.ONE_BY_ONE, MoveTarget.TOMORROW, total = 2, moved = 1)
        val oneLeft = evening.copy(tasks = evening.tasks.drop(1))

        val picking = withLeftovers(oneLeft, inProgress).byId("leftovers-$DAY-pick")!!
        assertEquals(listOf("Comprar regalo"), picking.tasks.map { it.title })
        assertEquals(MoveTarget.TOMORROW, picking.taskTarget)

        val finished = withLeftovers(evening.copy(tasks = emptyList()), inProgress.copy(done = 1))
        assertNull(finished.byId("leftovers-$DAY-pick"))
        assertEquals("Listo ✅ He movido 1 tarea y has hecho 1.", finished.byId("leftovers-$DAY-done")!!.text)
    }

    @Test
    fun `los repasos de dias anteriores se quedan en el chat en orden`() {
        val older = LeftoverOutcome(DAY.minusDays(2), LeftoverChoice.KEEP, MoveTarget.TOMORROW, total = 1)
        val newer = LeftoverOutcome(DAY.minusDays(1), LeftoverChoice.MOVE_ALL, MoveTarget.TOMORROW, total = 3, moved = 3)

        val questionIds = withLeftovers(evening, newer, older).messages
            .map { it.id }
            .filter { Regex("leftovers-\\d{4}-\\d{2}-\\d{2}").matches(it) }

        assertEquals(listOf("leftovers-${DAY.minusDays(2)}", "leftovers-${DAY.minusDays(1)}", "leftovers-$DAY"), questionIds)
    }

    // endregion

    // region Prioridades del día

    private val candidates = FocusCandidates(DAY, listOf(aTask(id = "t1", title = "Gimnasio"), aTask(id = "t2", title = "Banco")))

    @Test
    fun `por la manana pregunta por las prioridades con la lista marcable`() {
        val question = assistantConversation(DigestAnswer.ACCEPTED, true, focus = candidates).messages.last()

        assertEquals("focus-$DAY", question.id)
        assertEquals(MAX_FOCUS_TASKS, question.maxSelectable)
        assertEquals(listOf("Gimnasio", "Banco"), question.tasks.map { it.title })
        assertEquals(listOf(ChatReply.FOCUS_CONFIRM, ChatReply.FOCUS_SKIP), question.options.map { it.reply })
    }

    @Test
    fun `con una sola tarea no pregunta por prioridades`() {
        val chat = assistantConversation(DigestAnswer.ACCEPTED, true, focus = candidates.copy(tasks = candidates.tasks.take(1)))

        assertNull(chat.byId("focus-$DAY"))
    }

    @Test
    fun `si queda un repaso de pendientes sin contestar, las prioridades esperan`() {
        val morning = evening.copy(reviewDate = DAY.minusDays(1), target = MoveTarget.TODAY)

        val chat = assistantConversation(DigestAnswer.ACCEPTED, true, morning, focus = candidates)

        assertNull(chat.byId("focus-$DAY"))
    }

    @Test
    fun `elegidas se ven con estrellas y ya no se pregunta`() {
        val chosen = DailyFocus(DAY, listOf(FocusedTask("t2", "Banco"), FocusedTask("t1", "Gimnasio")))

        val chat = assistantConversation(DigestAnswer.ACCEPTED, true, focus = candidates, focusHistory = listOf(chosen))

        assertEquals("⭐ Banco\n⭐ Gimnasio", chat.byId("focus-$DAY-answer")!!.text)
        assertTrue(chat.optionMessages.isEmpty())
        assertTrue(chat.byId("focus-$DAY")!!.tasks.isEmpty())
    }

    @Test
    fun `hoy no tambien cierra la pregunta`() {
        val chat = assistantConversation(DigestAnswer.ACCEPTED, true, focus = candidates, focusHistory = listOf(DailyFocus(DAY, emptyList())))

        assertEquals("Hoy no", chat.byId("focus-$DAY-answer")!!.text)
        assertTrue(chat.optionMessages.isEmpty())
    }

    @Test
    fun `el repaso de ayer va antes que las prioridades de hoy y el de hoy despues`() {
        val yesterdayReview = LeftoverOutcome(DAY.minusDays(1), LeftoverChoice.KEEP, MoveTarget.TODAY, total = 1)
        val todayReview = LeftoverOutcome(DAY, LeftoverChoice.KEEP, MoveTarget.TOMORROW, total = 1)
        val chosen = DailyFocus(DAY, listOf(FocusedTask("t1", "Gimnasio")))

        val ids = assistantConversation(
            DigestAnswer.ACCEPTED, true,
            outcomes = listOf(todayReview, yesterdayReview),
            focusHistory = listOf(chosen)
        ).messages.map { it.id }.filter { Regex("(leftovers|focus)-\\d{4}-\\d{2}-\\d{2}").matches(it) }

        assertEquals(listOf("leftovers-${DAY.minusDays(1)}", "focus-$DAY", "leftovers-$DAY"), ids)
    }

    // endregion

    // region Ids, sin leer y vaciar

    @Test
    fun `los ids son unicos`() {
        val outcome = LeftoverOutcome(DAY.minusDays(1), LeftoverChoice.ONE_BY_ONE, MoveTarget.TOMORROW, total = 2)
        val chat = withLeftovers(evening, outcome)

        assertEquals(chat.messages.size, chat.messages.map { it.id }.toSet().size)
    }

    @Test
    fun `sin leer cuenta solo mensajes del asistente no vistos`() {
        val chat = assistantConversation(DigestAnswer.DECLINED, notificationsAllowed = false)
        val assistantIds = chat.messages.filter { it.fromAssistant }.map { it.id }

        assertEquals(assistantIds.size, chat.unreadCount(emptySet()))
        // La respuesta del usuario no cuenta aunque no esté marcada
        assertEquals(0, chat.unreadCount(assistantIds.toSet()))
        assertEquals(1, chat.unreadCount(assistantIds.dropLast(1).toSet()))
    }

    @Test
    fun `una pregunta de pendientes nueva cuenta como sin leer`() {
        val before = withLeftovers(null)
        val readIds = before.messages.map { it.id }.toSet()

        assertEquals(1, withLeftovers(evening).unreadCount(readIds))
    }

    @Test
    fun `vaciar el chat oculta esos mensajes pero no los que lleguen despues`() {
        val cleared = withLeftovers(null).messages.map { it.id }.toSet()

        val chat = assistantConversation(DigestAnswer.ACCEPTED, true, evening, clearedIds = cleared)

        assertEquals(listOf("leftovers-$DAY"), chat.messages.map { it.id })
    }

    // endregion

    // region Día de nómina

    private fun withPayday(payday: Payday) = assistantConversation(DigestAnswer.ACCEPTED, true, payday = payday)

    @Test
    fun `el dia de nomina saluda, aconseja y pregunta si se apunta`() {
        val chat = withPayday(Payday(DAY, salaryCents = null, answer = null))
        val question = chat.byId("payday-$DAY")!!

        assertTrue(question.text.contains("💼"))
        assertTrue(question.text.contains("aparta primero el ahorro"))
        assertTrue(question.text.contains("¿Apuntamos la nómina en Ahorros?"))
        assertEquals(listOf(ChatReply.PAYDAY_REGISTER, ChatReply.PAYDAY_LATER), question.options.map { it.reply })
    }

    @Test
    fun `con la nomina apuntada propone el reparto 50-30-20 en euros`() {
        val chat = withPayday(Payday(DAY, salaryCents = 185_000, answer = PaydayAnswer.REGISTER))
        val plan = chat.byId("payday-$DAY-plan")!!.text

        assertTrue(plan.contains("1.850,00 €"))
        assertTrue(plan.contains("Necesidades (50 %): 925,00 €"))
        assertTrue(plan.contains("Caprichos (30 %): 555,00 €"))
        assertTrue(plan.contains("Ahorro (20 %): 370,00 €"))
        // Ya contestado: sin botones
        assertTrue(chat.optionMessages.isEmpty())
        assertEquals("Apuntar nómina", chat.byId("payday-$DAY-answer")!!.text)
    }

    @Test
    fun `si la nomina ya estaba apuntada no pregunta, va directo al reparto`() {
        val chat = withPayday(Payday(DAY, salaryCents = 185_000, answer = null))

        assertFalse(chat.byId("payday-$DAY")!!.text.contains("¿Apuntamos"))
        assertTrue(chat.optionMessages.isEmpty())
        assertTrue(chat.byId("payday-$DAY-plan") != null)
    }

    @Test
    fun `apuntar sin importe aun avisa de que abre Ahorros, y ahora no lo deja para luego`() {
        assertTrue(withPayday(Payday(DAY, null, PaydayAnswer.REGISTER)).byId("payday-$DAY-open")!!.text.contains("Te abro Ahorros"))

        val later = withPayday(Payday(DAY, null, PaydayAnswer.LATER))
        assertEquals("Ahora no", later.byId("payday-$DAY-answer")!!.text)
        assertTrue(later.byId("payday-$DAY-later") != null)
        assertNull(later.byId("payday-$DAY-plan"))
    }

    @Test
    fun `sin nomina en Ajustes no hay mensajes de nomina`() {
        val chat = assistantConversation(DigestAnswer.ACCEPTED, true)

        assertTrue(chat.messages.none { it.id.startsWith("payday") })
    }

    // endregion
}
