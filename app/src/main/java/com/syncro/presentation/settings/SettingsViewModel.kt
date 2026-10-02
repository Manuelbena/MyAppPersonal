package com.syncro.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syncro.data.preferences.ThemeMode
import com.syncro.data.preferences.ThemePreferences
import com.syncro.domain.model.AppSettings
import com.syncro.domain.model.AssistantSettings
import com.syncro.domain.model.DataLossSummary
import com.syncro.domain.model.DigestSettings
import com.syncro.domain.model.User
import com.syncro.domain.usecase.CreateBackupUseCase
import com.syncro.domain.usecase.ExportMovementsCsvUseCase
import com.syncro.domain.usecase.GetDataLossSummaryUseCase
import com.syncro.domain.usecase.RestoreBackupUseCase
import com.syncro.domain.usecase.GetLocalUserUseCase
import com.syncro.domain.usecase.GetSettingsUseCase
import com.syncro.domain.usecase.LogoutUseCase
import com.syncro.domain.usecase.UpdateSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val user: User? = null,
    val settings: AppSettings = AppSettings(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Diálogo de cerrar sesión abierto, con lo que se perdería. */
    val logoutPrompt: DataLossSummary? = null,
    val isLoggingOut: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    getLocalUser: GetLocalUserUseCase,
    getSettings: GetSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    private val themePreferences: ThemePreferences,
    private val getDataLossSummary: GetDataLossSummaryUseCase,
    private val logout: LogoutUseCase,
    private val exportMovementsCsv: ExportMovementsCsvUseCase,
    private val createBackup: CreateBackupUseCase,
    private val restoreBackup: RestoreBackupUseCase
) : ViewModel() {

    private val dialog = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> = combine(
        getLocalUser(), getSettings(), themePreferences.themeMode, dialog
    ) { user, settings, theme, dialog ->
        dialog.copy(user = user, settings = settings, themeMode = theme)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** Avisos de un solo uso (p. ej. una hora no válida) para mostrar en un snackbar. */
    val messages = _messages.receiveAsFlow()

    fun updateDigest(change: (DigestSettings) -> DigestSettings) = update { it.copy(digest = change(it.digest)) }

    fun updateAssistant(change: (AssistantSettings) -> AssistantSettings) = update { it.copy(assistant = change(it.assistant)) }

    private fun update(change: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            updateSettings(change).onFailure { _messages.send(it.message ?: "No se pudo guardar el ajuste") }
        }
    }

    // region Tus datos: exportar y copias

    /** Ingresos y gastos en CSV; [write] guarda el texto en el archivo que eligió el usuario. */
    fun exportMovementsCsv(write: suspend (String) -> Unit) = fileJob {
        write(exportMovementsCsv())
        "Ingresos y gastos exportados ✅"
    }

    /** Copia de notas, movimientos y presupuestos; [write] la guarda donde eligió el usuario. */
    fun createBackup(write: suspend (String) -> Unit) = fileJob {
        write(createBackup())
        "Copia de seguridad guardada ✅"
    }

    /** Recupera la copia que lee [read] (el archivo elegido), sin borrar nada. */
    fun restoreBackup(read: suspend () -> String) = fileJob {
        restoreBackup(read()).fold(
            onSuccess = { restored ->
                if (restored.isEmpty) "La copia estaba vacía"
                else "Copia restaurada: ${count(restored.notes, "nota", "notas")}, " +
                    "${count(restored.movements, "movimiento", "movimientos")} y " +
                    "${count(restored.budgets, "presupuesto", "presupuestos")} ✅"
            },
            onFailure = { it.message ?: "No se pudo restaurar la copia" }
        )
    }

    /** Lee o escribe un archivo y avisa del resultado; si el sistema falla (sin espacio, sin permiso), lo dice. */
    private fun fileJob(job: suspend () -> String) {
        viewModelScope.launch {
            val message = try {
                job()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "No se pudo acceder al archivo. Inténtalo de nuevo."
            }
            _messages.send(message)
        }
    }

    private fun count(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "$n $many"

    // endregion

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { themePreferences.saveThemeMode(mode) }
    }

    /** Abre la confirmación con lo que se perdería, para que el usuario decida sabiéndolo. */
    fun requestLogout() {
        viewModelScope.launch { dialog.value = dialog.value.copy(logoutPrompt = getDataLossSummary()) }
    }

    fun dismissLogout() {
        dialog.value = dialog.value.copy(logoutPrompt = null)
    }

    /** Al borrarse la sesión, la app vuelve sola a la pantalla de inicio de sesión. */
    fun confirmLogout() {
        if (dialog.value.isLoggingOut) return
        dialog.value = dialog.value.copy(isLoggingOut = true)
        viewModelScope.launch {
            try {
                logout()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send("No se pudo cerrar la sesión. Inténtalo de nuevo.")
            } finally {
                dialog.value = dialog.value.copy(logoutPrompt = null, isLoggingOut = false)
            }
        }
    }
}
