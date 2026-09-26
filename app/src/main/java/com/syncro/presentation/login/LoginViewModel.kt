package com.syncro.presentation.login

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syncro.data.auth.GoogleSignInClient
import com.syncro.data.auth.SignInCancelledException
import com.syncro.domain.usecase.SignInWithGoogleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    // El selector de cuentas es UI del sistema y necesita el Context de la pantalla: por eso el
    // ViewModel lo usa directamente y pasa la cuenta elegida al caso de uso, que no sabe de Android
    private val googleSignInClient: GoogleSignInClient,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    fun signIn(context: Context) {
        val tag = "LoginViewModel"
        Log.d(tag, "Initiating sign-in")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = googleSignInClient.requestAccount(context)
                .fold(onSuccess = { account -> signInWithGoogleUseCase(account) }, onFailure = { Result.failure(it) })
            result.onSuccess {
                Log.d(tag, "Sign-in successful")
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            }.onFailure { error ->
                // Cancelar el selector no es un error: se vuelve a la pantalla sin mensaje
                val message = if (error is SignInCancelledException) null else error.message
                if (message != null) Log.e(tag, "Sign-in failed", error)
                _uiState.update { it.copy(isLoading = false, error = message) }
            }
        }
    }
}
