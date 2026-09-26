package com.syncro.domain.usecase

import com.syncro.domain.model.User
import com.syncro.domain.repository.UserRepository
import javax.inject.Inject

/**
 * Completa el inicio de sesión con la cuenta que eligió el usuario en el selector de Google:
 * la valida y la guarda como usuario actual (sustituyendo a cualquier cuenta anterior).
 */
class SignInWithGoogleUseCase @Inject constructor(
    private val repository: UserRepository
) {
    suspend operator fun invoke(account: User): Result<User> {
        // Toda la sincronización usa este email como cuenta de Google: si no es válido, no se guarda
        val email = account.email.trim()
        if (email.isBlank() || email.equals("null", ignoreCase = true) || "@" !in email) {
            return Result.failure(IllegalArgumentException("No se pudo obtener un email válido de la cuenta de Google"))
        }

        val user = account.copy(email = email)
        repository.saveUser(user)
        return Result.success(user)
    }
}
