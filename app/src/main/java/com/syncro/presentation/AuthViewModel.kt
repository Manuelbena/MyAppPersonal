package com.syncro.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syncro.domain.usecase.GetLocalUserUseCase
import com.syncro.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    getLocalUserUseCase: GetLocalUserUseCase
) : ViewModel() {
    val currentUser: StateFlow<User?> = getLocalUserUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
}
