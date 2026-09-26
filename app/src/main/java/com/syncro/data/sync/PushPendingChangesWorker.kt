package com.syncro.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.syncro.domain.repository.GoogleSyncRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.IOException

/** Sube a Google todos los cambios locales pendientes; lo lanza [SyncScheduler] al haber red. */
class PushPendingChangesWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    // Acceso a Hilt sin HiltWorkerFactory: evita configurar WorkManager a mano en la Application
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun googleSyncRepository(): GoogleSyncRepository
    }

    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors
            .fromApplication(applicationContext, WorkerEntryPoint::class.java)
            .googleSyncRepository()

        return repository.pushPendingChanges().fold(
            onSuccess = { Result.success() },
            onFailure = { if (isRetryable(it)) Result.retry() else Result.failure() }
        )
    }

    /**
     * Se reintenta ante errores de red o de servidor. No tiene sentido reintentar si falta
     * autorización del usuario (se pedirá al abrir la app) o si Google rechaza los datos.
     */
    private fun isRetryable(error: Throwable): Boolean = when (error) {
        is UserRecoverableAuthIOException -> false
        is GoogleJsonResponseException -> error.statusCode == 408 || error.statusCode == 429 || error.statusCode >= 500
        is IOException -> true
        else -> false
    }
}
