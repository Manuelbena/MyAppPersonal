package com.syncro.domain.usecase

import com.syncro.domain.repository.AccountDataRepository
import com.syncro.domain.repository.UserRepository
import javax.inject.Inject

/**
 * Cierra la sesión: primero borra los datos de la cuenta y después la sesión. En ese orden, si
 * algo falla a medias la sesión sigue abierta y se puede reintentar; al revés quedarían datos de
 * una cuenta a la vista de la siguiente.
 */
class LogoutUseCase @Inject constructor(
    private val accountData: AccountDataRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke() {
        accountData.clearAccountData()
        userRepository.clearUser()
    }
}
