package com.syncro

import android.app.Application
import com.syncro.data.notifications.DigestAlarmScheduler
import com.syncro.domain.repository.SettingsRepository
import com.syncro.presentation.widget.TodayWidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SyncroApp : Application() {

    @Inject lateinit var digestScheduler: DigestAlarmScheduler
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var todayWidgetUpdater: TodayWidgetUpdater

    // Vive lo que el proceso: no hay nada que cancelar
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Programa los avisos al arrancar (p. ej. tras instalar) y vuelve a programarlos cada vez
        // que el usuario cambia su hora o los apaga en Ajustes. Programar es idempotente
        appScope.launch {
            settingsRepository.settings
                .map { it.digest }
                .distinctUntilChanged()
                .collect { digestScheduler.scheduleAll(it) }
        }
        // El widget "Tu día" sigue los cambios de tareas y eventos mientras la app está viva
        appScope.launch { todayWidgetUpdater.run() }
    }
}
