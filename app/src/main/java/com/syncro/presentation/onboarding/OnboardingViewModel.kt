package com.syncro.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syncro.data.preferences.ThemeMode
import com.syncro.data.preferences.ThemePreferences
import com.syncro.domain.model.AppSettings
import com.syncro.domain.model.AssistantSettings
import com.syncro.domain.model.DigestSettings
import com.syncro.domain.usecase.GetLocalUserUseCase
import com.syncro.domain.usecase.GetSettingsUseCase
import com.syncro.domain.usecase.UpdateSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Lo que enseña la guía: el nombre para saludar y los ajustes tal como están ahora. */
data class OnboardingUiState(
    val firstName: String? = null,
    val settings: AppSettings = AppSettings(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

/**
 * La guía de inicio. Cada cambio se guarda al momento (si se cierra la app a medias, lo elegido se
 * queda), y al acabar o saltarla se marca como hecha para no volver a salir. Uno solo, en
 * MainScaffold: decide si se abre la guía al entrar y lo comparte con la pantalla.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    getLocalUser: GetLocalUserUseCase,
    getSettings: GetSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    private val themePreferences: ThemePreferences
) : ViewModel() {

    /** Si hay que enseñar la guía; null mientras se leen los ajustes. */
    val needsOnboarding: StateFlow<Boolean?> = getSettings()
        .map { !it.onboardingCompleted }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<OnboardingUiState> = combine(getLocalUser(), getSettings(), themePreferences.themeMode) { user, settings, theme ->
        OnboardingUiState(
            firstName = user?.name?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() },
            settings = settings,
            themeMode = theme
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OnboardingUiState())

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** Avisos de un solo uso, p. ej. una hora del resumen que no vale. */
    val messages = _messages.receiveAsFlow()

    fun updateDigest(change: (DigestSettings) -> DigestSettings) = update { it.copy(digest = change(it.digest)) }

    fun updateAssistant(change: (AssistantSettings) -> AssistantSettings) = update { it.copy(assistant = change(it.assistant)) }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { themePreferences.saveThemeMode(mode) }
    }

    /** Al acabar o al saltarla: no vuelve a salir (se puede repetir desde Ajustes). */
    fun finish() = update { it.copy(onboardingCompleted = true) }

    /** Desde Ajustes: la guía vuelve a salir. */
    fun restart() = update { it.copy(onboardingCompleted = false) }

    private fun update(change: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            updateSettings(change).onFailure { _messages.send(it.message ?: "No se pudo guardar") }
        }
    }
}
