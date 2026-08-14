package com.syncro.domain.usecase

import android.content.Context
import com.syncro.domain.model.User
import com.syncro.domain.repository.UserRepository
import javax.inject.Inject

class SignInWithGoogleUseCase @Inject constructor(
    private val repository: UserRepository
) {
    suspend operator fun invoke(context: Context): Result<User> {
        return repository.signInWithGoogle(context)
    }
}
