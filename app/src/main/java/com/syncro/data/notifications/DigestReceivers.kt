package com.syncro.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.syncro.domain.model.DigestMoment
import com.syncro.domain.model.toMessage
import com.syncro.domain.repository.SettingsRepository
import com.syncro.domain.usecase.GetDailyDigestUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Acceso a Hilt con EntryPoint, como PushPendingChangesWorker: sin @AndroidEntryPoint en receivers
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DigestEntryPoint {
    fun getDailyDigest(): GetDailyDigestUseCase
    fun notifier(): DigestNotifier
    fun scheduler(): DigestAlarmScheduler
    fun settings(): SettingsRepository
}

private fun Context.digestEntryPoint(): DigestEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, DigestEntryPoint::class.java)

/** Trabajo en segundo plano desde un receiver: goAsync, porque leer DataStore o Room no puede ir en el hilo principal. */
private fun BroadcastReceiver.runAsync(tag: String, block: suspend () -> Unit) {
    val pendingResult = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block()
        } catch (e: Exception) {
            Log.e(tag, "Fallo en el aviso diario", e)
        } finally {
            pendingResult.finish()
        }
    }
}

/** Suena a la hora del aviso: programa el de mañana y publica el de ahora. */
class DigestAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val moment = intent.getStringExtra(EXTRA_MOMENT)
            ?.let { name -> DigestMoment.entries.firstOrNull { it.name == name } }
            ?: return
        val entryPoint = context.digestEntryPoint()

        runAsync(TAG) {
            val settings = entryPoint.settings().settings.first().digest
            // Primero el de mañana: si algo falla al escribir el aviso, la cadena no se corta
            entryPoint.scheduler().schedule(moment, settings)
            // Por si se apagó justo antes de sonar
            if (!settings.isEnabled(moment)) return@runAsync
            entryPoint.getDailyDigest()(moment)?.let { digest ->
                entryPoint.notifier().show(moment, digest.toMessage())
            }
        }
    }

    companion object {
        const val ACTION_DIGEST = "com.syncro.action.DAILY_DIGEST"
        const val EXTRA_MOMENT = "moment"
        private const val TAG = "DailyDigest"
    }
}

/**
 * Android borra las alarmas al reiniciar el móvil y deja de cuadrar si cambia la hora o la zona:
 * en esos casos (y al actualizar la app) se vuelven a programar.
 */
class DigestRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESCHEDULE_ACTIONS) return
        val entryPoint = context.digestEntryPoint()
        runAsync("DailyDigest") {
            entryPoint.scheduler().scheduleAll(entryPoint.settings().settings.first().digest)
        }
    }

    private companion object {
        val RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
    }
}
