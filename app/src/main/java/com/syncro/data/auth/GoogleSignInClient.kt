package com.syncro.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.syncro.domain.model.User
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * Muestra el selector de cuentas de Google (Credential Manager) y devuelve la cuenta elegida.
 *
 * Vive fuera del dominio porque necesita un Context de Activity para mostrar la UI del sistema.
 * No guarda nada: validar y persistir la cuenta es trabajo de SignInWithGoogleUseCase.
 */
class GoogleSignInClient @Inject constructor() {

    suspend fun requestAccount(activityContext: Context): Result<User> {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(SERVER_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val credential = CredentialManager.create(activityContext)
                .getCredential(activityContext, request)
                .credential
            if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                Log.e(TAG, "Unexpected credential type: ${credential.type}")
                return Result.failure(IllegalStateException("Credential is not a GoogleIdTokenCredential"))
            }

            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            Result.success(
                User(
                    email = googleCredential.id,
                    name = googleCredential.displayName ?: "User",
                    photoUrl = googleCredential.profilePictureUri?.toString(),
                    idToken = googleCredential.idToken
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "GoogleSignIn"
        // Client ID de tipo "Web application" del proyecto de Google Cloud (no es un secreto)
        const val SERVER_CLIENT_ID = "155567191441-chsv0sh2fe8kmfougqer9kkkn6mghq9d.apps.googleusercontent.com"
    }
}
