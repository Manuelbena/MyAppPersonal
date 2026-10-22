package com.syncro.domain.model

import com.syncro.testutil.DAY
import com.syncro.testutil.aTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StuckTasksTest {

    private val threeReviews = setOf(DAY.minusDays(2), DAY.minusDays(1), DAY)

    @Test
    fun `una tarea con tres repasos sin hacerse esta atascada`() {
        val task = aTask(id = "banco", title = "Llamar al banco")

        val stuck = stuckTasks(listOf(task), mapOf("banco" to threeReviews), answered = emptySet())

        assertEquals(listOf(StuckTask(task, reviews = 3, lastReview = DAY)), stuck)
    }

    @Test
    fun `no cuentan las de pocos repasos, las hechas, las que se repiten ni las ya contestadas`() {
        val tasks = listOf(
            aTask(id = "dos"),
            aTask(id = "hecha", isCompleted = true),
            aTask(id = "serie").copy(seriesId = "s1"),
            aTask(id = "contestada")
        )
        val reviews = mapOf(
            "dos" to setOf(DAY.minusDays(1), DAY),
            "hecha" to threeReviews,
            "serie" to threeReviews,
            "contestada" to threeReviews
        )

        assertTrue(stuckTasks(tasks, reviews, answered = setOf("contestada")).isEmpty())
    }

    @Test
    fun `el asistente pregunta que hacer con ella detras del repaso`() {
        val stuck = StuckTask(aTask(id = "banco", title = "Llamar al banco"), reviews = 4, lastReview = DAY)
        val outcome = LeftoverOutcome(DAY, LeftoverChoice.MOVE_ALL, MoveTarget.TOMORROW, total = 1, moved = 1)

        val messages = assistantConversation(
            answer = DigestAnswer.ACCEPTED,
            notificationsAllowed = true,
            outcomes = listOf(outcome),
            stuck = listOf(stuck)
        ).messages

        val question = messages.last()
        assertEquals("stuck-banco", question.id)
        assertEquals("leftovers-$DAY-done", messages[messages.size - 2].id)
        assertTrue(question.text.startsWith("🐌 «Llamar al banco» ya lleva 4 repasos sin hacerse."))
        assertEquals(
            listOf(ChatReply.STUCK_DONE, ChatReply.STUCK_DELETE, ChatReply.STUCK_KEEP),
            question.options.map { it.reply }
        )
    }

    @Test
    fun `lo contestado queda en el chat sin opciones`() {
        val answer = StuckAnswer("banco", "Llamar al banco", reviews = 3, lastReview = DAY, choice = StuckChoice.DELETE)

        val messages = assistantConversation(
            answer = DigestAnswer.ACCEPTED,
            notificationsAllowed = true,
            stuck = listOf(StuckTask(aTask(id = "banco"), 3, DAY)),
            stuckAnswers = listOf(answer)
        ).messages.filter { it.id.startsWith("stuck-banco") }

        assertEquals(listOf("stuck-banco", "stuck-banco-answer", "stuck-banco-done"), messages.map { it.id })
        assertTrue(messages.all { it.options.isEmpty() })
        assertEquals("Ya no hace falta", messages[1].text)
    }
}
