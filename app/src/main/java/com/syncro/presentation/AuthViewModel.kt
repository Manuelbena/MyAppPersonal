package com.syncro.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syncro.domain.model.User
import com.syncro.domain.usecase.GetLocalUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Estado de la sesión. [Loading] existe para no confundir "aún no lo sé" con "no hay sesión":
 * antes el valor inicial era null y la app enseñaba el login un instante a quien ya había entrado.
 */
sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val user: User) : SessionState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    getLocalUserUseCase: GetLocalUserUseCase
) : ViewModel() {
    val session: StateFlow<SessionState> = getLocalUserUseCase()
        .map { user -> if (user == null) SessionState.LoggedOut else SessionState.LoggedIn(user) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SessionState.Loading
        )
}
