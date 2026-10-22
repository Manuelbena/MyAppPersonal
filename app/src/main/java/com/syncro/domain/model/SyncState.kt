package com.syncro.domain.model

/**
 * Cómo está la sincronización con Google: si hay conexión, cuántas tareas y eventos tienen
 * cambios que aún no han llegado a Google (incluidos los creados que nunca se subieron) y a qué
 * servicios de Google no nos deja entrar el usuario (no dio el permiso o lo quitó).
 */
data class SyncState(
    val isOnline: Boolean,
    val pendingChanges: Int,
    val missingAccess: Set<GoogleService> = emptySet()
)

/** Los servicios de Google con los que se sincroniza; cada uno tiene su propio permiso. */
enum class GoogleService { CALENDAR, TASKS }
