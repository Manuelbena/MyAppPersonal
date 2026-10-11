package com.syncro.domain.model

import com.syncro.testutil.anEvent
import com.syncro.testutil.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeklyBalanceTest {

    // Domingo 4 de octubre de 2026: la semana va del lunes 28 de septiembre a ese domingo
    private val sunday = LocalDate.of(2026, 10, 4)

    @Test
    fun `la semana es la ultima completa antes de hoy, de lunes a domingo`() {
        assertEquals(sunday, lastWeekEnd(LocalDate.of(2026, 10, 5))) // lunes
        assertEquals(sunday, lastWeekEnd(LocalDate.of(2026, 10, 11))) // domingo siguiente: aún no ha acabado
    }

    @Test
    fun `suma las horas por categoria, recortando lo que se sale de la semana`() {
        val events = listOf(
            anEvent(categoryText = "Trabajo", date = sunday.minusDays(6), startTime = at("09:00"), endTime = at("17:00")),
            anEvent(categoryText = "Trabajo", date = sunday.minusDays(5), startTime = at("09:00"), endTime = at("13:30")),
            // Del domingo 23:00 al lunes 01:00: solo cuenta una hora
            anEvent(categoryText = "Ocio", date = sunday, endDate = sunday.plusDays(1), startTime = at("23:00"), endTime = at("01:00")),
            // Todo el día: no cuenta
            anEvent(categoryText = "Personal", date = sunday, startTime = at("00:00"), endTime = at("00:00"))
        )

        val balance = weeklyBalance(events, sunday)!!

        assertEquals(mapOf("Trabajo" to 750L, "Ocio" to 60L), balance.minutesByCategory)
    }

    @Test
    fun `una semana casi vacia no se cuenta`() {
        val events = listOf(anEvent(date = sunday, startTime = at("10:00"), endTime = at("10:30")))

        assertNull(weeklyBalance(events, sunday))
    }

    @Test
    fun `el lunes el asistente cuenta la semana y avisa si solo hubo trabajo`() {
        val balance = WeeklyBalance(sunday, mapOf("Trabajo" to 34 * 60L, "Salud" to 45L))

        val message = assistantConversation(answer = DigestAnswer.ACCEPTED, notificationsAllowed = true, weeklyBalance = balance)
            .messages.single { it.id == "week-$sunday" }

        assertTrue(message.text, message.text.startsWith("📊 Así fue tu semana: 💼 Trabajo 34 h · 🩺 Salud 45 min."))
        assertTrue(message.text, "nada de Ocio ni Deporte" in message.text)
    }
}
