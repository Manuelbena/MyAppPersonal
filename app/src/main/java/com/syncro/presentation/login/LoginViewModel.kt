package com.syncro.presentation.login

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    fun signIn(context: Context) {
        val tag = "LoginViewModel"
        Log.d(tag, "Initiating sign-in")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = signInWithGoogleUseCase(context)
            result.onSuccess {
                Log.d(tag, "Sign-in successful")
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            }.onFailure { error ->
                Log.e(tag, "Sign-in failed", error)
                _uiState.update { it.copy(isLoading = false, error = error.message) }
            }
        }
    }
}
