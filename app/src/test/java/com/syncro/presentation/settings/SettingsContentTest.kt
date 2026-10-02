package com.syncro.presentation.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.syncro.data.preferences.ThemeMode
import com.syncro.domain.model.AppSettings
import com.syncro.domain.model.AssistantSettings
import com.syncro.domain.model.DataLossSummary
import com.syncro.domain.model.DigestSettings
import com.syncro.domain.model.User
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pantalla de Ajustes: muestra el perfil, los interruptores cambian el ajuste que dicen, el tema
 * se elige, y cerrar sesión avisa de lo que se perdería antes de hacerlo.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsContentTest {

    @get:Rule
    val compose = createComposeRule()

    private var settings = AppSettings()
    private val calls = mutableListOf<String>()

    private fun show(state: SettingsUiState = SettingsUiState(user = User("ana@example.com", "Ana García", null), settings = settings)) {
        compose.setContent {
            SettingsContent(
                state = state,
                notificationsAllowed = true,
                appVersion = "1.0",
                onBack = { calls += "back" },
                onNotificationsToggle = { calls += "notifications:$it" },
                onDigestChange = { change -> settings = settings.copy(digest = change(settings.digest)) },
                onAssistantChange = { change -> settings = settings.copy(assistant = change(settings.assistant)) },
                onThemeChange = { calls += "theme:$it" },
                onLogoutClick = { calls += "logout?" },
                onLogoutConfirm = { calls += "logout!" },
                onLogoutDismiss = { calls += "cancel" },
                onOpenLegal = { calls += "legal:$it" }
            )
        }
    }

    @Test
    fun `muestra el perfil de la cuenta`() {
        show()

        compose.onNodeWithText("Ana García").assertIsDisplayed()
        compose.onNodeWithText("ana@example.com").assertIsDisplayed()
    }

    @Test
    fun `la lista muestra cada categoria con su estado y volver regresa a ella`() {
        show()

        compose.onNodeWithText("09:00 · 21:00").assertIsDisplayed()
        compose.onNodeWithText("Sistema").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Notificaciones").performClick()
        compose.onNodeWithText("Recibir notificaciones").assertIsDisplayed()

        compose.onNodeWithContentDescription("Volver").performClick()

        compose.onNodeWithText("Resumen del día").assertIsDisplayed()
        assertEquals("Volver desde un apartado no sale de Ajustes", emptyList<String>(), calls)
        compose.onNodeWithContentDescription("Volver").performClick()
        assertEquals(listOf("back"), calls)
    }

    @Test
    fun `privacidad y legal lleva a cada documento`() {
        show()

        compose.onNodeWithText("Privacidad y legal").performScrollTo().performClick()
        compose.onNodeWithText("Política de privacidad").performClick()

        assertEquals(listOf("legal:PRIVACY"), calls)
    }

    @Test
    fun `cerrar sesion esta dentro de Cuenta`() {
        show()

        compose.onNodeWithText("Ana García").performClick()
        compose.onNodeWithText("Cerrar sesión").performClick()

        assertEquals(listOf("logout?"), calls)
    }

    @Test
    fun `las notificaciones se activan con un interruptor`() {
        show()

        compose.onNodeWithText("Notificaciones").performClick()
        compose.onNodeWithText("Activadas", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Recibir notificaciones").performClick()

        assertEquals(listOf("notifications:false"), calls)
    }

    @Test
    fun `los interruptores cambian su ajuste`() {
        show()

        compose.onNodeWithText("Resumen del día").performClick()
        compose.onNodeWithText("Resumen de la mañana").performClick()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithText("Asistente").performScrollTo().performClick()
        compose.onNodeWithText("Prioridades del día").performClick()

        assertEquals(DigestSettings(morningEnabled = false), settings.digest)
        compose.onNodeWithText("Avisos de presupuesto").performClick()

        compose.onNodeWithText("Frase del día").performScrollTo().performClick()

        assertEquals(AssistantSettings(focusEnabled = false, budgetAlertsEnabled = false, dailyQuoteEnabled = false), settings.assistant)
    }

    @Test
    fun `el dia de nomina empieza sin nomina y se elige en un dialogo`() {
        show()
        compose.onNodeWithText("Asistente").performScrollTo().performClick()

        compose.onNodeWithText("No tengo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Día de nómina").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Día 28").performScrollTo().performClick()
        compose.onNodeWithText("Guardar").performClick()

        assertEquals(28, settings.assistant.paydayDay)
    }

    @Test
    fun `no tengo nomina quita el dia elegido`() {
        settings = AppSettings(assistant = AssistantSettings(paydayDay = 28))
        show()
        compose.onNodeWithText("Asistente").performScrollTo().performClick()

        compose.onNodeWithText("Día 28").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Día de nómina").performScrollTo().performClick()
        compose.onNodeWithText("No tengo nómina").performClick()
        compose.onNodeWithText("Guardar").performClick()

        assertEquals(null, settings.assistant.paydayDay)
    }

    @Test
    fun `el tema se elige entre sistema, claro y oscuro`() {
        show()

        compose.onNodeWithText("Apariencia").performScrollTo().performClick()
        compose.onNodeWithText("Oscuro").performClick()

        assertEquals(listOf("theme:${ThemeMode.DARK}"), calls)
    }

    @Test
    fun `cerrar sesion avisa de cambios sin subir y de las notas`() {
        show(
            SettingsUiState(
                user = User("ana@example.com", "Ana", null),
                logoutPrompt = DataLossSummary(unsyncedChanges = 2, notes = 1)
            )
        )

        compose.onNodeWithText("Tienes 2 cambios", substring = true).assertIsDisplayed()
        compose.onNodeWithText("perderás 1 nota", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()

        assertEquals(listOf("cancel"), calls)
    }
}
