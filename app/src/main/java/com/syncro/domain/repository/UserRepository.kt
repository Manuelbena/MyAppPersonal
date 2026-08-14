package com.syncro.domain.repository

import com.syncro.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getUser(): Flow<User?>
    suspend fun saveUser(user: User)
    suspend fun clearUser()
    suspend fun signInWithGoogle(context: android.content.Context): Result<User>
}
