package com.syncro.domain.repository

import kotlinx.coroutines.flow.Flow

/** Si el móvil tiene conexión a internet ahora mismo. */
interface ConnectivityRepository {
    /** Emite el estado actual al suscribirse y después cada vez que cambia. */
    val isOnline: Flow<Boolean>
}
