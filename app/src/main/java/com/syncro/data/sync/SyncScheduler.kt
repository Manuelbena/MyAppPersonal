package com.syncro.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Programa la subida de cambios pendientes para cuando haya red. WorkManager persiste la
 * petición, así que se ejecuta aunque la app se cierre o el móvil se reinicie.
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun schedulePendingPush() {
        val request = OneTimeWorkRequestBuilder<PushPendingChangesWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // KEEP: si ya hay una subida programada, subirá también este cambio
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private companion object {
        const val WORK_NAME = "push_pending_changes"
    }
}
