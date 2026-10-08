package com.syncro.presentation.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.syncro.data.preferences.ThemeMode
import com.syncro.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * La guía de inicio recorrida como un usuario: saludo, avisos, nómina, asistente, tema y resumen
 * final, más "Saltar" y "atrás". Riesgos: un paso que no guarda lo que se toca, un resumen que no
 * dice lo elegido, y no poder salir de la guía.
 */
@RunWith(RobolectricTestRunner::class)
class OnboardingContentTest {

    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(OnboardingUiState(firstName = "Ana"))
    private var notificationsAllowed by mutableStateOf(false)
    private var askedForNotifications = 0
    private var finished = 0

    private fun show() {
        compose.setContent {
            OnboardingContent(
                state = state,
                notificationsAllowed = notificationsAllowed,
                onEnableNotifications = {
                    askedForNotifications++
                    notificationsAllowed = true
                },
                onDigestChange = { change -> state = state.copy(settings = state.settings.copy(digest = change(state.settings.digest))) },
                onAssistantChange = { change -> state = state.copy(settings = state.settings.copy(assistant = change(state.settings.assistant))) },
                onThemeChange = { state = state.copy(themeMode = it) },
                onFinish = { finished++ },
                showMascot = false
            )
        }
    }

    private fun next() = compose.onNodeWithText("Siguiente").performClick()

    @Test
    fun `saluda por el nombre y cuenta lo que hace la app`() {
        show()

        compose.onNodeWithText("¡Hola, Ana! 👋").assertIsDisplayed()
        compose.onNodeWithText("Tu dinero bajo control").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Empezar ✨").assertIsDisplayed()
    }

    @Test
    fun `se puede saltar en cualquier momento`() {
        show()
        compose.onNodeWithText("Empezar ✨").performClick()

        compose.onNodeWithText("Saltar").performClick()

        assertEquals(1, finished)
    }

    @Test
    fun `los avisos piden el permiso y dejan elegir los resumenes`() {
        show()
        compose.onNodeWithText("Empezar ✨").performClick()

        compose.onNodeWithContentDescription("Paso 1 de 4").assertExists()
        compose.onNodeWithText("🔔  Activar notificaciones").performScrollTo().performClick()
        compose.onNodeWithText("Notificaciones activadas").assertIsDisplayed()
        compose.onNodeWithContentDescription("Resumen de la noche").performScrollTo().performClick()

        assertEquals(1, askedForNotifications)
        assertFalse(state.settings.digest.eveningEnabled)
    }

    @Test
    fun `elegir el dia de cobro lo guarda y explica como iran los meses`() {
        show()
        compose.onNodeWithText("Empezar ✨").performClick()
        next()

        compose.onNodeWithContentDescription("Día 27").performScrollTo().performClick()

        assertEquals(27, state.settings.assistant.paydayDay)
        compose.onNodeWithContentDescription("Día 27").assertIsSelected()
        compose.onNodeWithText("Cobras el día 27: tus meses irán del 27 al 26 del siguiente.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `el asistente viene encendido y se puede apagar lo que no se quiera`() {
        show()
        compose.onNodeWithText("Empezar ✨").performClick()
        next()
        next()

        compose.onNodeWithText("Tu asistente personal").assertIsDisplayed()
        compose.onNodeWithContentDescription("Frase del día").performScrollTo().performClick()

        assertFalse(state.settings.assistant.dailyQuoteEnabled)
    }

    @Test
    fun `atras vuelve al paso anterior`() {
        show()
        compose.onNodeWithText("Empezar ✨").performClick()
        next()

        compose.onNodeWithContentDescription("Paso anterior").performClick()

        compose.onNodeWithText("Que no se te escape nada").assertIsDisplayed()
    }

    @Test
    fun `el tema se elige con una vista previa y el resumen final dice lo elegido`() {
        state = state.copy(settings = AppSettings().let { it.copy(assistant = it.assistant.copy(paydayDay = 27)) })
        notificationsAllowed = true
        show()
        compose.onNodeWithText("Empezar ✨").performClick()
        repeat(3) { next() }

        compose.onNodeWithContentDescription("Tema Oscuro").performClick()
        assertEquals(ThemeMode.DARK, state.themeMode)
        next()

        compose.onNodeWithText("¡Todo listo, Ana!").assertIsDisplayed()
        compose.onNodeWithText("🔔 Notificaciones activadas").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("☀️ 9:00 · 🌙 21:00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("💶 Nómina el día 27").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("🌙 Tema oscuro").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Ir a mi día 🚀").performClick()
        assertEquals(1, finished)
    }
}
