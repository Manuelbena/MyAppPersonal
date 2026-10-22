package com.syncro.domain.repository

import com.syncro.domain.model.AppUpdate

interface AppUpdateRepository {
    /**
     * La versión nueva disponible en la tienda, o null si la instalada es la última o no se puede
     * saber (sin conexión, la app no se instaló desde Google Play…). Nunca falla.
     */
    suspend fun availableUpdate(): AppUpdate?
}
