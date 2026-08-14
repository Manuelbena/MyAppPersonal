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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao
) : UserRepository {

    override fun getUser(): Flow<User?> {
        return userDao.getUser().map { entity ->
            entity?.let {
                User(
                    email = it.email,
                    name = it.name,
                    photoUrl = it.photoUrl,
                    idToken = it.idToken
                )
            }
        }
    }

    override suspend fun saveUser(user: User) {
        userDao.saveUser(
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
        val tag = "GoogleSignIn"
        Log.d(tag, "Starting Google Sign-In process")
        val credentialManager = CredentialManager.create(context)
        
        val serverClientId = "155567191441-chsv0sh2fe8kmfougqer9kkkn6mghq9d.apps.googleusercontent.com"
        Log.d(tag, "Using Server Client ID: $serverClientId")

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false) 
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(false) 
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            Log.d(tag, "Requesting credentials...")
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            Log.d(tag, "Credential received: ${credential.type}")
            
            if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val userEmail = googleIdTokenCredential.id
                
                Log.d(tag, "GoogleIdTokenCredential parsed. ID (email): '$userEmail', Name: '${googleIdTokenCredential.displayName}'")
                
                if (userEmail.isBlank() || userEmail == "null") {
                    Log.e(tag, "Retrieved email is invalid: '$userEmail'")
                    Result.failure(Exception("Could not retrieve a valid email from Google account"))
                } else {
                    val user = User(
                        email = userEmail,
                        name = googleIdTokenCredential.displayName ?: "User",
                        photoUrl = googleIdTokenCredential.profilePictureUri?.toString(),
                        idToken = googleIdTokenCredential.idToken
                    )
                    saveUser(user)
                    Log.d(tag, "User saved successfully with email: ${user.email}")
                    Result.success(user)
                }
            } else {
                Log.e(tag, "Unexpected credential type: ${credential.type}")
                Result.failure(Exception("Credential is not a GoogleIdTokenCredential"))
            }
        } catch (e: Exception) {
            Log.e(tag, "Error during Google Sign-In", e)
            Log.e(tag, "Error message: ${e.message}")
            Result.failure(e)
        }
    }
}
