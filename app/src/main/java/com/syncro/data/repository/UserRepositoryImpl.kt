package com.syncro.data.repository

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.syncro.data.local.dao.UserDao
import com.syncro.data.local.entity.UserEntity
import com.syncro.domain.model.User
import com.syncro.domain.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao
) : UserRepository {

    override fun getUser(): Flow<User?> {
        return userDao.getUser().map { it?.toDomain() }
    }

    override suspend fun saveUser(user: User) {
        userDao.replaceUser(
            UserEntity(
                email = user.email,
                name = user.name,
                photoUrl = user.photoUrl,
                idToken = user.idToken
            )
        )
    }

    override suspend fun clearUser() {
        userDao.deleteUser()
    }

    override suspend fun signInWithGoogle(context: Context): Result<User> {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(SERVER_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val credential = CredentialManager.create(context).getCredential(context, request).credential
            if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                Log.e(TAG, "Unexpected credential type: ${credential.type}")
                return Result.failure(IllegalStateException("Credential is not a GoogleIdTokenCredential"))
            }

            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val email = googleCredential.id
            if (email.isBlank() || email.equals("null", ignoreCase = true)) {
                Log.e(TAG, "Google account returned an invalid email")
                return Result.failure(IllegalStateException("Could not retrieve a valid email from Google account"))
            }

            val user = User(
                email = email,
                name = googleCredential.displayName ?: "User",
                photoUrl = googleCredential.profilePictureUri?.toString(),
                idToken = googleCredential.idToken
            )
            saveUser(user)
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            Result.failure(e)
        }
    }

    private fun UserEntity.toDomain() = User(
        email = email,
        name = name,
        photoUrl = photoUrl,
        idToken = idToken
    )

    private companion object {
        const val TAG = "GoogleSignIn"
        // Client ID de tipo "Web application" del proyecto de Google Cloud (no es un secreto)
        const val SERVER_CLIENT_ID = "155567191441-chsv0sh2fe8kmfougqer9kkkn6mghq9d.apps.googleusercontent.com"
    }
}
