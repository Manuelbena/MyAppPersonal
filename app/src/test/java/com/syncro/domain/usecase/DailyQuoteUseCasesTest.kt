package com.syncro.domain.usecase

import com.syncro.domain.model.AppSettings
import com.syncro.domain.model.AssistantSettings
import com.syncro.testutil.DAY
import com.syncro.testutil.FakeDailyQuoteRepository
import com.syncro.testutil.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.ZoneOffset

/**
 * Plan: cuándo sale la frase del día en Inicio. Riesgos: que la ✕ la oculte para siempre (debe
 * volver mañana, con otra frase) y que se vea con el ajuste apagado.
 */
class DailyQuoteUseCasesTest {

    private val settings = FakeSettingsRepository()
    private val quotes = FakeDailyQuoteRepository()

    private fun clockOn(day: java.time.LocalDate) = Clock.fixed(day.atTime(10, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private fun observe(clock: Clock) = ObserveDailyQuoteUseCase(settings, quotes, GetDailyQuoteUseCase(clock), clock)

    @Test
    fun `por defecto sale la frase de hoy`() = runTest {
        val clock = clockOn(DAY)

        assertEquals(GetDailyQuoteUseCase(clock)(), observe(clock)().first())
    }

    @Test
    fun `cerrarla la oculta hoy y manana vuelve con otra frase`() = runTest {
        HideDailyQuoteUseCase(quotes, clockOn(DAY))()

        assertNull(observe(clockOn(DAY))().first())
        val tomorrow = observe(clockOn(DAY.plusDays(1)))().first()
        assertNotEquals(GetDailyQuoteUseCase(clockOn(DAY))(), tomorrow)
        assertEquals(GetDailyQuoteUseCase(clockOn(DAY.plusDays(1)))(), tomorrow)
    }

    @Test
    fun `apagada en Ajustes no sale nunca`() = runTest {
        settings.current.value = AppSettings(assistant = AssistantSettings(dailyQuoteEnabled = false))

        assertNull(observe(clockOn(DAY))().first())
    }
}
