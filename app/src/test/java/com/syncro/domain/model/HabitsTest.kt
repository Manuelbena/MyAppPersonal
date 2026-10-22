package com.syncro.domain.model

import com.syncro.testutil.aTask
import com.syncro.testutil.anEvent
import com.syncro.testutil.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HabitsTest {

    // Miércoles 7 de octubre de 2026; los martes anteriores: 6, 29 sep, 22 sep…
    private val today = LocalDate.of(2026, 10, 7)
    private val tuesday = LocalDate.of(2026, 10, 6)

    private fun gym(date: LocalDate, id: String = "gym-$date") =
        anEvent(id = id, title = "Gimnasio", date = date, startTime = at("19:00"), endTime = at("20:00"), categoryText = "Deporte")

    @Test
    fun `tres martes con gimnasio en las ultimas semanas son un habito`() {
        val events = listOf(gym(tuesday), gym(tuesday.minusWeeks(1)), gym(tuesday.minusWeeks(3)))

        val habit = findHabits(emptyList(), events, today).single()

        assertEquals("Gimnasio", habit.title)
        assertEquals(DayOfWeek.TUESDAY, habit.weekday)
        assertEquals(3, habit.weeks)
        assertEquals(tuesday, habit.lastDone)
        assertEquals("gimnasio-tuesday", habit.key)
    }

    @Test
    fun `sin mayusculas ni espacios que cuenten, y con tareas tambien`() {
        val tasks = listOf(
            aTask(title = "Sacar la basura", date = tuesday),
            aTask(title = "sacar la basura ", date = tuesday.minusWeeks(1)),
            aTask(title = "Sacar la basura", date = tuesday.minusWeeks(2))
        )

        assertEquals(listOf("Sacar la basura"), findHabits(tasks, emptyList(), today).map { it.title })
    }

    @Test
    fun `no es habito con dos semanas, en dias distintos, ni si ya se repite`() {
        assertTrue(findHabits(emptyList(), listOf(gym(tuesday), gym(tuesday.minusWeeks(1))), today).isEmpty())
        // Martes, miércoles y jueves: tres veces, pero ningún día tres semanas
        val mixed = listOf(gym(tuesday), gym(tuesday.minusWeeks(1).plusDays(1)), gym(tuesday.minusWeeks(2).plusDays(2)))
        assertTrue(findHabits(emptyList(), mixed, today).isEmpty())
        val repeating = listOf(gym(tuesday), gym(tuesday.minusWeeks(1)), gym(tuesday.minusWeeks(2)), gym(tuesday.plusWeeks(1)).copy(seriesId = "s1"))
        assertTrue(findHabits(emptyList(), repeating, today).isEmpty())
    }

    @Test
    fun `solo se propone justo despues de hacerlo`() {
        val old = listOf(gym(tuesday.minusWeeks(1)), gym(tuesday.minusWeeks(2)), gym(tuesday.minusWeeks(3)))

        assertTrue(findHabits(emptyList(), old, today).isEmpty())
    }

    @Test
    fun `la base de la repeticion es la ultima vez apuntada, aunque sea futura`() {
        val events = listOf(gym(tuesday), gym(tuesday.minusWeeks(1)), gym(tuesday.minusWeeks(2)), gym(tuesday.plusWeeks(1), id = "next"))

        assertEquals("next", (findHabits(emptyList(), events, today).single().latest as SyncroItem.Event).id)
    }

    @Test
    fun `el asistente pregunta y, contestado, lo confirma sin volver a preguntar`() {
        val habit = findHabits(emptyList(), listOf(gym(tuesday), gym(tuesday.minusWeeks(1)), gym(tuesday.minusWeeks(2))), today).single()

        val question = assistantConversation(DigestAnswer.ACCEPTED, true, habits = listOf(habit))
            .messages.single { it.id == "habit-gimnasio-tuesday" }
        assertEquals("🔁 Veo que «Gimnasio» lo sueles apuntar los martes. ¿Quieres que se repita solo cada martes?", question.text)
        assertEquals(listOf("Sí, cada martes", "No, gracias"), question.options.map { it.label })

        val answered = assistantConversation(
            DigestAnswer.ACCEPTED, true,
            habits = listOf(habit),
            habitAnswers = listOf(HabitAnswer(habit.key, "Gimnasio", DayOfWeek.TUESDAY, tuesday, accepted = true))
        ).messages.filter { it.id.startsWith("habit-") }
        assertEquals(listOf("habit-gimnasio-tuesday", "habit-gimnasio-tuesday-answer", "habit-gimnasio-tuesday-done"), answered.map { it.id })
        assertTrue(answered.all { it.options.isEmpty() })
    }
}
