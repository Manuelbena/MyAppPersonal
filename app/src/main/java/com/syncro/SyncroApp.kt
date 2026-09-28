package com.syncro

import android.app.Application
import com.syncro.data.notifications.DigestAlarmScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SyncroApp : Application() {

    @Inject lateinit var digestScheduler: DigestAlarmScheduler

    override fun onCreate() {
        super.onCreate()
        // Idempotente: asegura los avisos de las 9:00 y las 21:00 (p. ej. tras instalar la app)
        digestScheduler.scheduleAll()
    }
}
