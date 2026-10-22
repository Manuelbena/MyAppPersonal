package com.syncro.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.syncro.data.update.PlayAppUpdateRepository
import com.syncro.domain.model.AppUpdate
import com.syncro.domain.repository.AppUpdateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Solo debug: finge que hay una versión nueva en Google Play, para ver el mensaje del asistente
 * (una build debug no viene de Play, así que Play nunca le ofrece nada).
 *
 * Fingir: adb shell am broadcast -n com.manuelgalindo.syncro/com.syncro.debug.FakeUpdateReceiver
 *         (opcional --ei version 999; luego cerrar y abrir la app, que mira al arrancar)
 * Quitar: adb shell am broadcast -n com.manuelgalindo.syncro/com.syncro.debug.FakeUpdateReceiver --es action clear
 */
class FakeUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (intent.getStringExtra("action") == "clear") {
            prefs.edit().remove(KEY_VERSION).apply()
            Log.i(TAG, "Versión nueva fingida quitada")
        } else {
            val version = intent.getIntExtra("version", DEFAULT_FAKE_VERSION)
            prefs.edit().putInt(KEY_VERSION, version).apply()
            Log.i(TAG, "Versión nueva fingida: $version")
        }
    }

    companion object {
        const val PREFS = "debug_fake_update"
        const val KEY_VERSION = "version"
        private const val DEFAULT_FAKE_VERSION = 9999
        private const val TAG = "FakeUpdate"
    }
}

/** La de Google Play, salvo que [FakeUpdateReceiver] haya fingido una versión nueva. */
class DebugAppUpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val play: PlayAppUpdateRepository
) : AppUpdateRepository {
    override suspend fun availableUpdate(): AppUpdate? {
        val prefs = context.getSharedPreferences(FakeUpdateReceiver.PREFS, Context.MODE_PRIVATE)
        val fake = prefs.getInt(FakeUpdateReceiver.KEY_VERSION, -1)
        return if (fake > 0) AppUpdate(fake) else play.availableUpdate()
    }
}
