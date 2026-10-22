package com.syncro.data.update

import android.content.Context
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import com.syncro.domain.model.AppUpdate
import com.syncro.domain.repository.AppUpdateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import com.google.android.play.core.ktx.requestAppUpdateInfo
import javax.inject.Inject

/**
 * Pregunta a Google Play (la librería de actualizaciones de la app) si hay una versión más nueva.
 * Fuera de Google Play (depuración, instalada a mano) Play no la conoce y falla: entonces no hay nada.
 */
class PlayAppUpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : AppUpdateRepository {

    private val manager by lazy { AppUpdateManagerFactory.create(context) }

    override suspend fun availableUpdate(): AppUpdate? = try {
        val info = manager.requestAppUpdateInfo()
        if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) AppUpdate(info.availableVersionCode()) else null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo comprobar si hay versión nueva", e)
        null
    }

    private companion object {
        const val TAG = "AppUpdate"
    }
}
