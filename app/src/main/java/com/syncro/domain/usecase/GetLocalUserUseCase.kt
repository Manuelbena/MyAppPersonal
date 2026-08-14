package com.syncro.domain.usecase

import com.syncro.domain.model.User
import com.syncro.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetLocalUserUseCase @Inject constructor(
    private val repository: UserRepository
) {
    operator fun invoke(): Flow<User?> {
        return repository.getUser()
    }
}
