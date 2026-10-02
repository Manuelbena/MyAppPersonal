package com.syncro.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.syncro.domain.repository.ConnectivityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/**
 * La conexión según la red por defecto del sistema. Basta con que la red diga tener internet
 * (no se exige que Android la haya validado): en redes que bloquean la comprobación de Google
 * la sync funciona igual, y si de verdad no hay salida la sync falla y la UI lo avisa aparte.
 */
class NetworkConnectivityRepository @Inject constructor(
    @ApplicationContext context: Context
) : ConnectivityRepository {

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    override val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasInternet())
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        // El estado de ahora: si no hay ninguna red, el callback no avisaría de nada
        trySend(connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.hasInternet() == true)
        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged().conflate()

    private fun NetworkCapabilities.hasInternet() = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
