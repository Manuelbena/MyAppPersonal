package com.syncro.presentation.onboarding

import androidx.test.core.app.ApplicationProvider
import com.syncro.data.preferences.ThemePreferences
import com.syncro.domain.model.User
import com.syncro.domain.usecase.GetLocalUserUseCase
import com.syncro.domain.usecase.GetSettingsUseCase
import com.syncro.domain.usecase.UpdateSettingsUseCase
import com.syncro.testutil.FakeSettingsRepository
import com.syncro.testutil.FakeUserRepository
import com.syncro.testutil.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalTime

/**
 * Plan: la guía de inicio. Responsabilidades: salir solo hasta que se acaba o se salta, guardar
 * cada cambio al momento, saludar por el nombre y poder repetirla. Riesgos: que vuelva a salir
 * cada vez que se abre la app, perder lo elegido si se sale a medias y aceptar horas imposibles.
 */
@RunWith(RobolectricTestRunner::class)
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val users = FakeUserRepository()
    private val theme = ThemePreferences(ApplicationProvider.getApplicationContext())

    private fun createViewModel() = OnboardingViewModel(
        getLocalUser = GetLocalUserUseCase(users),
        getSettings = GetSettingsUseCase(settings),
        updateSettings = UpdateSettingsUseCase(settings),
        themePreferences = theme
    )

    @Test
    fun `sale la primera vez y deja de salir al acabarla o saltarla`() = runTest {
        val viewModel = createViewModel()
        assertEquals(true, viewModel.needsOnboarding.value)

        viewModel.finish()

        assertEquals(false, viewModel.needsOnboarding.value)
        assertTrue(settings.current.value.onboardingCompleted)
    }

    @Test
    fun `desde Ajustes se puede repetir`() = runTest {
        settings.current.value = settings.current.value.copy(onboardingCompleted = true)
        val viewModel = createViewModel()

        viewModel.restart()

        assertEquals(true, viewModel.needsOnboarding.value)
    }

    @Test
    fun `cada cambio se guarda al momento`() = runTest {
        val viewModel = createViewModel()

        viewModel.updateAssistant { it.copy(paydayDay = 27, homeSavingsEnabled = true) }
        viewModel.updateDigest { it.copy(morningTime = LocalTime.of(8, 0), eveningEnabled = false) }

        val saved = settings.current.value
        assertEquals(27, saved.assistant.paydayDay)
        assertTrue(saved.assistant.homeSavingsEnabled)
        assertEquals(LocalTime.of(8, 0), saved.digest.morningTime)
        assertFalse(saved.digest.eveningEnabled)
    }

    @Test
    fun `una hora de la manana despues de la noche no se guarda y se avisa`() = runTest {
        val viewModel = createViewModel()
        val messages = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.messages.toList(messages) }

        viewModel.updateDigest { it.copy(morningTime = LocalTime.of(22, 0)) }

        assertEquals(LocalTime.of(9, 0), settings.current.value.digest.morningTime)
        assertEquals(1, messages.size)
    }

    @Test
    fun `saluda por el nombre de pila`() = runTest {
        users.user.value = User(email = "ana@example.com", name = "Ana García López", photoUrl = null)
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals("Ana", viewModel.uiState.first { it.firstName != null }.firstName)
    }
}
