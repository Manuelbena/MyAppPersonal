package com.syncro.domain.usecase

import com.syncro.domain.model.Quote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

class GetDailyQuoteUseCaseTest {

    private val quotes = (1..10).map { Quote("Frase $it", "Autor $it") }

    private fun quoteOn(date: LocalDate) =
        GetDailyQuoteUseCase(Clock.fixed(date.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC))(quotes)

    @Test
    fun `es la misma durante todo el dia`() {
        val date = LocalDate.of(2026, 9, 26)
        val morning = GetDailyQuoteUseCase(Clock.fixed(date.atTime(0, 1).toInstant(ZoneOffset.UTC), ZoneOffset.UTC))(quotes)
        val night = GetDailyQuoteUseCase(Clock.fixed(date.atTime(23, 59).toInstant(ZoneOffset.UTC), ZoneOffset.UTC))(quotes)

        assertEquals(morning, night)
    }

    @Test
    fun `cambia de un dia al siguiente`() {
        assertNotEquals(quoteOn(LocalDate.of(2026, 9, 26)), quoteOn(LocalDate.of(2026, 9, 27)))
    }

    @Test
    fun `el ultimo dia de un año bisiesto no se sale de la lista`() {
        // Valor límite: dayOfYear = 366 solo existe en años bisiestos
        quoteOn(LocalDate.of(2028, 12, 31))
    }
}
