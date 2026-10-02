package com.syncro.domain.model

/**
 * Cómo está la sincronización con Google: si hay conexión y cuántas tareas y eventos tienen
 * cambios que aún no han llegado a Google (incluidos los creados que nunca se subieron).
 */
data class SyncState(val isOnline: Boolean, val pendingChanges: Int)
